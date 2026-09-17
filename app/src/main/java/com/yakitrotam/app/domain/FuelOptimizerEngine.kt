package com.yakitrotam.app.domain

import com.yakitrotam.app.data.model.*
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.util.GeoUtils

/**
 * Rota üzerindeki yakıt tüketimini simüle eder ve duraklara karar verir.
 *
 * İki temel kural:
 * 1. Bir istasyon ancak **mevcut yakıtla gerçekten ulaşılabiliyorsa** durak olabilir.
 * 2. "Yola yakın" olmak yetmez: adayların gerçek yol ağındaki ek mesafesi [DetourResolver]
 *    ile ölçülür. Otoyola kuş uçuşu 100 m uzaktaki bir istasyon, otoyoldan çıkıp geri
 *    dönmeyi gerektirdiği için 15-30 km ek yol demek olabilir; böyle adaylar elenir.
 *    Çözücü yoksa veya servise ulaşılamıyorsa kuş uçuşu sapma tahminine geri düşülür.
 */
class FuelOptimizerEngine(
    private val stationRepository: GasStationRepository
) {

    /** İstasyonun rotaya izdüşürülmüş hali. [detourKm] kuş uçuşu, tek yön. */
    private data class Candidate(
        val station: GasStation,
        val alongKm: Double,
        val detourKm: Double
    )

    /** Ek yolu (gerçek ya da tahmini) belirlenmiş ve puanlanmış aday. Düşük puan daha iyi. */
    private data class Scored(
        val candidate: Candidate,
        val extraKm: Double,
        val extraMinutes: Double?,
        val score: Double
    )

    fun calculateTripPlan(
        origin: CityLocation,
        destination: CityLocation,
        routePoints: List<LatLng>,
        vehicleProfile: VehicleProfile,
        preferredBrands: Set<FuelBrand> = emptySet(),
        fuelPrice: FuelPriceSnapshot = FuelPriceSnapshot.fallback(),
        /** Kullanıcının alternatiflerden seçtiği istasyonlar: durak sırası -> istasyon kimliği. */
        forcedStationIds: Map<Int, String> = emptyMap(),
        /** Ağ çağrısı yapabilir; bu yüzden fonksiyon arka plan iş parçacığında çağrılmalıdır. */
        detourResolver: DetourResolver? = null
    ): TripPlanResult {
        val cumulative = GeoUtils.cumulativeDistancesKm(routePoints)
        val totalDistanceKm = cumulative.lastOrNull() ?: 0.0

        val consumptionRate = vehicleProfile.consumptionPer100Km.coerceAtLeast(1.0)
        val tankCapacity = vehicleProfile.tankCapacityLiters.coerceAtLeast(10.0)
        val reserveLiters = vehicleProfile.reserveLiters.coerceIn(0.0, tankCapacity * 0.5)
        val pricePerLiter = fuelPrice.priceFor(vehicleProfile.fuelType)

        // Marka tercihi burada filtre değil, puanlama kriteridir: tercih edilen marka
        // menzil içinde yoksa sürücüyü yolda bırakmak yerine başka bir istasyon önerilir.
        val candidates = stationRepository
            .filterStations(vehicleProfile.fuelType)
            .map { station ->
                val projection = GeoUtils.projectOntoRoute(station.location, routePoints, cumulative)
                Candidate(station, projection.alongKm, projection.detourKm)
            }
            .filter { it.detourKm <= MAX_DETOUR_KM }
            .sortedBy { it.alongKm }

        val stops = mutableListOf<FuelStop>()
        var alongKm = 0.0
        var fuelLiters = vehicleProfile.currentFuelLiters.coerceIn(0.0, tankCapacity)
        var detourDistanceKm = 0.0
        var warning: String? = null

        while (stops.size < MAX_STOPS) {
            val usableLiters = fuelLiters - reserveLiters
            val remainingKm = totalDistanceKm - alongKm
            val litersToFinish = remainingKm.toLiters(consumptionRate)

            // Rezerve dokunmadan varış noktasına yetiyorsa durak gerekmez.
            if (usableLiters >= litersToFinish) break

            val usedStationIds = stops.mapTo(HashSet()) { it.station.id }
            val reachable = candidates.filter { candidate ->
                candidate.station.id !in usedStationIds &&
                    candidate.alongKm > alongKm + MIN_LEG_KM &&
                    (candidate.alongKm - alongKm + candidate.detourKm)
                        .toLiters(consumptionRate) <= usableLiters
            }

            if (reachable.isEmpty()) {
                warning = if (candidates.isEmpty()) {
                    "Bu güzergahta OpenStreetMap üzerinde kayıtlı akaryakıt istasyonu bulunamadı."
                } else {
                    "Kalan ${usableLiters.toKm(consumptionRate).toInt()} km menzil içinde uygun istasyon " +
                        "bulunamadı. Yola çıkmadan önce depoyu doldurmanız önerilir."
                }
                break
            }

            val fromKm = alongKm
            val farthestAlongKm = reachable.maxOf { it.alongKm }
            // Pencere menzille orantılı: kısa menzilde 45 km'lik tolerans aracı erken durdururdu.
            val windowKm = minOf(SELECTION_WINDOW_KM, (farthestAlongKm - fromKm) * 0.25)
            val forced = reachable.firstOrNull { it.station.id == forcedStationIds[stops.size + 1] }

            fun evaluate(pool: List<Candidate>, resolver: DetourResolver? = detourResolver): List<Scored> =
                scoreCandidates(
                    pool = pool,
                    fromKm = fromKm,
                    farthestAlongKm = farthestAlongKm,
                    usableLiters = usableLiters,
                    consumptionRate = consumptionRate,
                    routePoints = routePoints,
                    totalDistanceKm = totalDistanceKm,
                    preferredBrands = preferredBrands,
                    fuelType = vehicleProfile.fuelType,
                    detourResolver = resolver
                )

            val windowPool = reachable.filter { it.alongKm >= farthestAlongKm - windowKm }
            var evaluated = evaluate(shortlist(windowPool, forced))

            // Penceredeki en iyi aday bile yoldan ciddi sapma gerektiriyorsa (ör. otoyoldan
            // çıkış), menzilin ikinci yarısındaki daha erken istasyonlara da bakılır:
            // yol üstünde 30 km önce durmak, 15 km'lik sapmadan iyidir.
            val bestInWindow = evaluated.minByOrNull { it.score }
            if (detourResolver != null && (bestInWindow == null || bestInWindow.extraKm > WIDEN_IF_EXTRA_KM)) {
                val widerPool = reachable.filter {
                    it.alongKm < farthestAlongKm - windowKm &&
                        it.alongKm >= fromKm + (farthestAlongKm - fromKm) * 0.5
                }
                if (widerPool.isNotEmpty()) evaluated = evaluated + evaluate(shortlist(widerPool, null))
            }

            // Gerçek ek yol hesaba katılınca hiçbir aday menzile girmiyorsa kuş uçuşu tahmine dön;
            // rezerv payı olduğu için sürücüyü duraksız bırakmaktan iyidir.
            if (evaluated.isEmpty()) evaluated = evaluate(shortlist(windowPool, forced), resolver = null)
            val chosen = evaluated.firstOrNull { it.candidate.station.id == forced?.station?.id }
                ?: evaluated.minBy { it.score }

            // Aynı bölgedeki diğer seçenekler. OSM'de aynı istasyon hem nokta hem alan olarak
            // kayıtlı olabildiği için seçilen durağa 300 m'den yakın olanlar tekrar sayılmaz.
            val alternatives = evaluated
                .filter {
                    it.candidate.station.id != chosen.candidate.station.id &&
                        GeoUtils.distanceKm(it.candidate.station.location, chosen.candidate.station.location) > 0.3
                }
                .sortedBy { it.score }
                .take(MAX_ALTERNATIVES)
                .map { StopAlternative(it.candidate.station, it.candidate.alongKm, it.extraKm / 2, it.extraMinutes) }

            val legKm = chosen.candidate.alongKm - alongKm
            val oneWayDetourKm = chosen.extraKm / 2
            val burnedLiters = (legKm + oneWayDetourKm).toLiters(consumptionRate)
            val arrivalFuel = (fuelLiters - burnedLiters).coerceAtLeast(0.0)
            val refuelLiters = tankCapacity - arrivalFuel

            stops.add(
                FuelStop(
                    stopIndex = stops.size + 1,
                    station = chosen.candidate.station,
                    distanceFromOriginKm = chosen.candidate.alongKm,
                    legDistanceKm = legKm,
                    arrivalFuelLevelPercent = (arrivalFuel / tankCapacity) * 100.0,
                    arrivalFuelLiters = arrivalFuel,
                    refuelLiters = refuelLiters,
                    estimatedRefuelCostTL = refuelLiters * pricePerLiter,
                    detourDistanceKm = oneWayDetourKm,
                    alternatives = alternatives,
                    detourMinutes = chosen.extraMinutes
                )
            )

            detourDistanceKm += chosen.extraKm
            alongKm = chosen.candidate.alongKm
            // Depo dolu çıkılır, rotaya dönüş yakıtı hemen düşülür.
            fuelLiters = tankCapacity - oneWayDetourKm.toLiters(consumptionRate)
        }

        // Son durakta depoyu tam doldurmak, varışa 30 km kala dolum yapan sürücüye
        // gereksiz bir fatura çıkarır. Sadece varışa + rezerve + %15 pay kadar alınır.
        if (stops.isNotEmpty()) {
            val last = stops.last()
            val neededLiters = (totalDistanceKm - last.distanceFromOriginKm + last.detourDistanceKm)
                .toLiters(consumptionRate)
            val targetLiters = (neededLiters + reserveLiters) * FINAL_FILL_BUFFER
            val trimmedRefuel = (targetLiters - last.arrivalFuelLiters)
                .coerceIn(0.0, tankCapacity - last.arrivalFuelLiters)

            if (trimmedRefuel < last.refuelLiters) {
                stops[stops.lastIndex] = last.copy(
                    refuelLiters = trimmedRefuel,
                    estimatedRefuelCostTL = trimmedRefuel * pricePerLiter
                )
                fuelLiters = last.arrivalFuelLiters + trimmedRefuel -
                    last.detourDistanceKm.toLiters(consumptionRate)
            }
        }

        val drivenKm = totalDistanceKm + detourDistanceKm
        val totalFuelConsumed = drivenKm.toLiters(consumptionRate)
        val arrivalFuel = (fuelLiters - (totalDistanceKm - alongKm).toLiters(consumptionRate))
            .coerceAtLeast(0.0)

        return TripPlanResult(
            origin = origin,
            destination = destination,
            totalDistanceKm = totalDistanceKm,
            totalDrivenDistanceKm = drivenKm,
            estimatedDrivingTimeMinutes = estimateMinutes(totalDistanceKm, stops),
            stops = stops,
            totalFuelConsumedLiters = totalFuelConsumed,
            totalEstimatedCostTL = totalFuelConsumed * pricePerLiter,
            totalRefuelCostTL = stops.sumOf { it.estimatedRefuelCostTL },
            arrivalFuelLiters = arrivalFuel,
            routePoints = routePoints,
            vehicleProfile = vehicleProfile,
            preferredBrands = preferredBrands,
            fuelPrice = fuelPrice,
            warning = warning
        )
    }

    /**
     * Yol sorgusuna gidecek adayları sınırlar: rotaya kuş uçuşu en yakın olanlar gerçekten
     * yol üstünde olmaya en yatkın olanlardır. Kullanıcının seçtiği istasyon her zaman dahildir.
     */
    private fun shortlist(pool: List<Candidate>, forced: Candidate?): List<Candidate> {
        val closest = pool.sortedBy { it.detourKm }.take(SHORTLIST_SIZE)
        return if (forced == null || forced in closest) closest else closest + forced
    }

    /**
     * Adayların ek yolunu belirler (çözücü varsa gerçek yol ağından, yoksa kuş uçuşu
     * sapmanın iki katı olarak), bu ek yolla hâlâ ulaşılabilir olanları puanlar.
     */
    private fun scoreCandidates(
        pool: List<Candidate>,
        fromKm: Double,
        farthestAlongKm: Double,
        usableLiters: Double,
        consumptionRate: Double,
        routePoints: List<LatLng>,
        totalDistanceKm: Double,
        preferredBrands: Set<FuelBrand>,
        fuelType: FuelType,
        detourResolver: DetourResolver?
    ): List<Scored> {
        if (pool.isEmpty()) return emptyList()

        val roadDetours = detourResolver?.let { resolver ->
            // Tüm adayları kapsayan iki rota noktası: sapma bu ikisi arasındaki farktan ölçülür.
            val startKm = (pool.minOf { it.alongKm } - DETOUR_ANCHOR_MARGIN_KM).coerceAtLeast(fromKm)
            val endKm = (pool.maxOf { it.alongKm } + DETOUR_ANCHOR_MARGIN_KM).coerceAtMost(totalDistanceKm)
            resolver.resolve(
                from = GeoUtils.findPointAtDistance(routePoints, startKm),
                to = GeoUtils.findPointAtDistance(routePoints, endKm),
                stations = pool.map { it.station.location }
            )
        }

        return pool.mapIndexedNotNull { index, candidate ->
            val road = roadDetours?.getOrNull(index)
            // Yol verisi geldiyse ama bu istasyon için yoksa, yol ağından ulaşılamıyor demektir.
            if (roadDetours != null && road == null) return@mapIndexedNotNull null

            val extraKm = road?.extraKm ?: (candidate.detourKm * 2)
            val reachLiters = (candidate.alongKm - fromKm + extraKm / 2).toLiters(consumptionRate)
            if (reachLiters > usableLiters) return@mapIndexedNotNull null

            val brandPenalty = when {
                preferredBrands.isEmpty() -> 0.0
                candidate.station.brand in preferredBrands -> 0.0
                else -> NON_PREFERRED_BRAND_PENALTY
            }
            val unknownFuelPenalty = if (candidate.station.confirmsFuel(fuelType)) 0.0 else 2.0
            val unnamedPenalty = if (candidate.station.brand == FuelBrand.DIGER) 3.0 else 0.0
            val score = extraKm * 1.5 +
                (road?.extraMinutes ?: 0.0) * 0.5 +
                (farthestAlongKm - candidate.alongKm) * 0.2 +
                brandPenalty + unknownFuelPenalty + unnamedPenalty

            Scored(candidate, extraKm, road?.extraMinutes, score)
        }
    }

    /** Otoyol ağırlıklı ortalama 92 km/s + durak başına 12 dk mola + duraklara sapma süresi. */
    private fun estimateMinutes(routeKm: Double, stops: List<FuelStop>): Int {
        val detourMinutes = stops.sumOf { it.detourMinutes ?: (it.extraRoadKm / 50.0 * 60.0) }
        return ((routeKm / 92.0) * 60 + stops.size * 12 + detourMinutes).toInt()
    }

    private fun Double.toLiters(consumptionPer100Km: Double): Double =
        (this / 100.0) * consumptionPer100Km

    private fun Double.toKm(consumptionPer100Km: Double): Double =
        (this / consumptionPer100Km) * 100.0

    private companion object {
        const val MAX_STOPS = 12
        const val MAX_DETOUR_KM = 6.0
        const val MIN_LEG_KM = 5.0

        /** Son durağa en fazla bu kadar km kala alternatif istasyonlar da değerlendirilir. */
        const val SELECTION_WINDOW_KM = 45.0

        /** Tek yol sorgusunda değerlendirilen en fazla aday (OSRM tablo isteği küçük kalsın). */
        const val SHORTLIST_SIZE = 24

        /** En iyi adayın gerçek ek yolu bunu aşıyorsa daha erken istasyonlara da bakılır. */
        const val WIDEN_IF_EXTRA_KM = 3.0

        /** Sapma ölçümünde adayların önüne ve arkasına bırakılan rota payı. */
        const val DETOUR_ANCHOR_MARGIN_KM = 2.0

        const val MAX_ALTERNATIVES = 3

        /** Son dolumda varış ihtiyacının üzerine bırakılan güvenlik payı. */
        const val FINAL_FILL_BUFFER = 1.15

        /** ~30 km ek yola bedel: tercih dışı markaya ancak gerçekten alternatif yoksa gidilir. */
        const val NON_PREFERRED_BRAND_PENALTY = 45.0
    }
}
