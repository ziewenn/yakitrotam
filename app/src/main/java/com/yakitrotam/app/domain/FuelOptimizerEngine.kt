package com.yakitrotam.app.domain

import com.yakitrotam.app.data.model.*
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.util.GeoUtils

/**
 * Rota üzerindeki yakıt tüketimini simüle eder ve duraklara karar verir.
 *
 * Temel kural: bir istasyon ancak **mevcut yakıtla gerçekten ulaşılabiliyorsa** durak olabilir.
 * (Eski sürüm ulaşılamayan istasyonları da seçip kalan yakıtı 0.5 L'ye kırpıyordu,
 * bu da depoya sığmayan mesafeler için gerçekçi olmayan planlar üretiyordu.)
 */
class FuelOptimizerEngine(
    private val stationRepository: GasStationRepository
) {

    /** İstasyonun rotaya izdüşürülmüş hali. */
    private data class Candidate(
        val station: GasStation,
        val alongKm: Double,
        val detourKm: Double
    )

    fun calculateTripPlan(
        origin: CityLocation,
        destination: CityLocation,
        routePoints: List<LatLng>,
        vehicleProfile: VehicleProfile,
        preferredBrands: Set<FuelBrand> = emptySet(),
        fuelPrice: FuelPriceSnapshot = FuelPriceSnapshot.fallback(),
        /** Kullanıcının alternatiflerden seçtiği istasyonlar: durak sırası -> istasyon kimliği. */
        forcedStationIds: Map<Int, String> = emptyMap()
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

            val farthestAlongKm = reachable.maxOf { it.alongKm }
            val rank = { candidate: Candidate ->
                score(candidate, farthestAlongKm, preferredBrands, vehicleProfile.fuelType)
            }
            // Pencere menzille orantılı: kısa menzilde 45 km'lik tolerans aracı erken durdururdu.
            val windowKm = minOf(SELECTION_WINDOW_KM, (farthestAlongKm - alongKm) * 0.25)
            val forcedId = forcedStationIds[stops.size + 1]
            val chosen = reachable.firstOrNull { it.station.id == forcedId }
                ?: reachable.filter { it.alongKm >= farthestAlongKm - windowKm }.minBy(rank)

            // Aynı bölgedeki diğer seçenekler. OSM'de aynı istasyon hem nokta hem alan olarak
            // kayıtlı olabildiği için seçilen durağa 300 m'den yakın olanlar tekrar sayılmaz.
            val alternatives = reachable
                .filter {
                    it.station.id != chosen.station.id &&
                        it.alongKm >= chosen.alongKm - ALTERNATIVE_RANGE_KM &&
                        GeoUtils.distanceKm(it.station.location, chosen.station.location) > 0.3
                }
                .sortedBy(rank)
                .take(MAX_ALTERNATIVES)
                .map { StopAlternative(it.station, it.alongKm, it.detourKm) }

            val legKm = chosen.alongKm - alongKm
            // Sapma yakıtı: istasyona gidiş rotadan ayrılmayı gerektirir.
            val burnedLiters = (legKm + chosen.detourKm).toLiters(consumptionRate)
            val arrivalFuel = (fuelLiters - burnedLiters).coerceAtLeast(0.0)
            val refuelLiters = tankCapacity - arrivalFuel

            stops.add(
                FuelStop(
                    stopIndex = stops.size + 1,
                    station = chosen.station,
                    distanceFromOriginKm = chosen.alongKm,
                    legDistanceKm = legKm,
                    arrivalFuelLevelPercent = (arrivalFuel / tankCapacity) * 100.0,
                    arrivalFuelLiters = arrivalFuel,
                    refuelLiters = refuelLiters,
                    estimatedRefuelCostTL = refuelLiters * pricePerLiter,
                    detourDistanceKm = chosen.detourKm,
                    alternatives = alternatives
                )
            )

            detourDistanceKm += chosen.detourKm * 2 // gidiş + rotaya dönüş
            alongKm = chosen.alongKm
            // Depo dolu çıkılır, rotaya dönüş yakıtı hemen düşülür.
            fuelLiters = tankCapacity - chosen.detourKm.toLiters(consumptionRate)
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
            estimatedDrivingTimeMinutes = estimateMinutes(drivenKm, stops.size),
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
     * Düşük puan daha iyi. Hedef mümkün olduğunca ileri gitmek (az durak), ama bunu
     * az sapma ve tercih edilen marka için birkaç km feda edebilmek.
     */
    private fun score(
        candidate: Candidate,
        farthestAlongKm: Double,
        preferredBrands: Set<FuelBrand>,
        fuelType: FuelType
    ): Double {
        val brandPenalty = when {
            preferredBrands.isEmpty() -> 0.0
            candidate.station.brand in preferredBrands -> 0.0
            else -> NON_PREFERRED_BRAND_PENALTY
        }
        val unknownFuelPenalty = if (candidate.station.confirmsFuel(fuelType)) 0.0 else 2.0
        val unnamedPenalty = if (candidate.station.brand == FuelBrand.DIGER) 3.0 else 0.0
        return candidate.detourKm * 3.0 +
            (farthestAlongKm - candidate.alongKm) * 0.2 +
            brandPenalty + unknownFuelPenalty + unnamedPenalty
    }

    /** Otoyol ağırlıklı ortalama 92 km/s + durak başına 12 dk mola. */
    private fun estimateMinutes(distanceKm: Double, stopCount: Int): Int =
        ((distanceKm / 92.0) * 60).toInt() + stopCount * 12

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

        /** Alternatif istasyonlar seçilen durağın en fazla bu kadar km gerisinden aranır. */
        const val ALTERNATIVE_RANGE_KM = 40.0
        const val MAX_ALTERNATIVES = 3

        /** Son dolumda varış ihtiyacının üzerine bırakılan güvenlik payı. */
        const val FINAL_FILL_BUFFER = 1.15

        /** ~15 km sapmaya bedel: tercih dışı markaya ancak gerçekten alternatif yoksa gidilir. */
        const val NON_PREFERRED_BRAND_PENALTY = 45.0
    }
}
