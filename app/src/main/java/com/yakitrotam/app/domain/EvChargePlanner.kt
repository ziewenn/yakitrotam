package com.yakitrotam.app.domain

import com.yakitrotam.app.data.model.*
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.util.GeoUtils
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Elektrikli araç için şarj durağı planı. Yakıt motorunun ([FuelOptimizerEngine]) yerine geçer,
 * aynı sonuç türlerini üretir; özet, paylaşım ve harita ekranları değişmeden çalışır.
 *
 * Yalnızca DC hızlı şarj kullanılır: yol üstünde AC ile şarj saatler sürer.
 * Kurallar:
 * - Bir şarj istasyonuna bataryada en az [VehicleProfile.reserveThresholdPercent] kalmışken varılır.
 * - Durak puanı dakikadır: sapma süresi + şarj süresi + gereksiz erken durma + fazladan durak cezası.
 * - Yalnızca gereken kadar şarj edilir; normalde %80'e kadar (sonrası çok yavaştır). Son durakta
 *   varışa %90 yetiyorsa oraya kadar şarj edilir: birkaç dakika fazla şarj, ayrı bir durak açmaktan
 *   iyidir. %80 bir sonraki istasyona yetmiyorsa önceki durak %100'e kadar uzatılır.
 * - Rakım ve soğuk hava modellenmez; kullanıcının girdiği tüketim ve rezerv payı bunu karşılar.
 */
class EvChargePlanner(
    private val stationRepository: GasStationRepository
) {
    private data class Candidate(
        val station: GasStation,
        val alongKm: Double,
        /** Kuş uçuşu, tek yön. */
        val crowDetourKm: Double,
        /** İstasyonun bu araca uyan soketinin gücü. */
        val stationKw: Double
    )

    private data class Scored(
        val candidate: Candidate,
        /** Gidiş-dönüş toplam ek yol. */
        val extraKm: Double,
        val extraMinutes: Double?,
        val score: Double
    )

    fun calculateTripPlan(
        origin: CityLocation,
        destination: CityLocation,
        routePoints: List<LatLng>,
        vehicleProfile: VehicleProfile,
        fuelPrice: FuelPriceSnapshot = FuelPriceSnapshot.fallback(),
        /** Kullanıcının alternatiflerden seçtiği istasyonlar: durak sırası -> istasyon kimliği. */
        forcedStationIds: Map<Int, String> = emptyMap(),
        /** Ağ çağrısı yapabilir; arka plan iş parçacığında çağrılmalıdır. */
        detourResolver: DetourResolver? = null,
        routeDurationMinutes: Double? = null
    ): TripPlanResult {
        val cumulative = GeoUtils.cumulativeDistancesKm(routePoints)
        val total = cumulative.lastOrNull() ?: 0.0

        val battery = vehicleProfile.tankCapacityLiters.coerceAtLeast(5.0)
        val kwhPerKm = vehicleProfile.consumptionPer100Km.coerceAtLeast(5.0) / 100.0
        val minPct = vehicleProfile.reserveThresholdPercent.coerceIn(5.0, 50.0)
        // Rezerv şarj istasyonuna varış içindir; varış noktası hedefi kullanıcının seçtiğidir.
        val destPct = vehicleProfile.evArrivalPercent.coerceIn(5.0, MAX_CHARGE_PERCENT)
        val eMin = battery * minPct / 100
        val eDest = battery * destPct / 100
        val eMax = battery * MAX_CHARGE_PERCENT / 100
        val eFinish = battery * FINISH_CHARGE_PERCENT / 100
        val carKw = vehicleProfile.maxChargeKw.coerceAtLeast(10.0)
        val connector = vehicleProfile.connector
        val defaultPrice = FuelType.ELEKTRIK.fallbackPricePerLiterTL

        fun kwh(km: Double) = km * kwhPerKm

        /** Bu km'den (sapma dönüşü dahil) varışa hedef yüzdeyle ulaşmak için gereken şarj yüzdesi. */
        fun needPct(alongKm: Double, oneWayDetourKm: Double) =
            (kwh(total - alongKm + oneWayDetourKm) + eDest) / battery * 100

        /** [km]'de şarj edilirse varışa kadar en az kaç durak daha gerekir (son bacak %90'a kadar). */
        fun stopsAfter(km: Double): Int {
            val need = kwh(total - km)
            val lastLeg = eFinish - eDest
            if (need <= lastLeg) return 0
            return ceil((need - lastLeg) / (eMax - eMin)).toInt()
        }

        /** [driveKm] sürüldükten sonra bataryada kalan yüzde. */
        fun arrivalPercent(energyNow: Double, driveKm: Double) = (energyNow - kwh(driveKm)) / battery * 100

        /**
         * Durakta hedeflenen çıkış yüzdesi: varışa %90 yetiyorsa o kadar, yetmiyorsa %80. İstasyona
         * zaten %80'in üstünde varılıyorsa durmak ancak daha fazla şarj için anlamlıdır: gereken
         * kadar (en çok %100). Böylece 0 kWh'lik boş durak çıkmaz.
         */
        fun departureFor(alongKm: Double, oneWayDetourKm: Double, arrivalPct: Double): Double {
            val need = needPct(alongKm, oneWayDetourKm)
            val target = if (need <= FINISH_CHARGE_PERCENT) need else MAX_CHARGE_PERCENT
            return if (target > arrivalPct + 1.0) target else min(100.0, max(need, arrivalPct))
        }

        val candidates = stationRepository.getAllStations()
            .mapNotNull { station ->
                val stationKw = station.dcKwFor(connector) ?: return@mapNotNull null
                val projection = GeoUtils.projectOntoRoute(station.location, routePoints, cumulative)
                if (projection.detourKm > MAX_DETOUR_KM) return@mapNotNull null
                Candidate(station, projection.alongKm, projection.detourKm, stationKw)
            }
            .sortedBy { it.alongKm }

        val stops = mutableListOf<FuelStop>()
        var alongKm = 0.0
        var energy = battery * vehicleProfile.currentLevelPercent.coerceIn(0.0, 100.0) / 100
        var raisedStop = -1
        var warning: String? = null

        while (stops.size < MAX_STOPS) {
            if (energy - eDest >= kwh(total - alongKm) - 1e-9) break

            val usedIds = stops.mapTo(HashSet()) { it.station.id }
            // Art arda iki durak olmasın diye bir sonraki durak en az birkaç km ileride aranır;
            // ilk durakta bu sınır yok: şarjı az olan sürücünün hemen yanındaki istasyon da sayılır.
            val minLeg = if (stops.isEmpty()) 0.0 else MIN_LEG_KM
            // Batarya zaten rezervde ya da hemen üstündeyse ilk istasyona rezervin altında varmak
            // kaçınılmazdır; o bacakta yalnızca sert alt sınır (%3) aranır.
            val lowStart = stops.isEmpty() && energy - eMin < kwh(LOW_START_KM)
            val floor = if (lowStart) min(eMin, battery * HARD_MIN_PERCENT / 100) else eMin
            val reachable = candidates.filter {
                it.station.id !in usedIds &&
                    it.alongKm > alongKm + minLeg &&
                    kwh(it.alongKm - alongKm + it.crowDetourKm) <= energy - floor + 1e-9
            }

            if (reachable.isEmpty()) {
                // Son durak bir öncekine çok yakınsa (ör. 6 km), onu çıkarıp öncekini %100'e kadar
                // uzatmak bir durak kazandırır: aynı noktada iki kez durmak gereksiz.
                val previous = stops.getOrNull(stops.size - 2)
                val latest = stops.lastOrNull()
                val previousDeparture = previous?.departurePercent ?: 100.0
                if (previous != null && latest != null && raisedStop != previous.stopIndex &&
                    latest.distanceFromOriginKm - previous.distanceFromOriginKm <= MERGE_STOPS_KM &&
                    previousDeparture < 100.0
                ) {
                    stops.removeAt(stops.lastIndex)
                    alongKm = previous.distanceFromOriginKm
                    val newDeparture = min(100.0, ceil(needPct(previous.distanceFromOriginKm, previous.detourDistanceKm) - 1e-9))
                        .coerceAtLeast(previousDeparture)
                    energy = battery * newDeparture / 100 - kwh(previous.detourDistanceKm)
                    val previousStationKw = previous.station.dcKwFor(connector) ?: (previous.chargeKw ?: carKw)
                    stops[stops.lastIndex] = withCharge(previous, battery, newDeparture, previousStationKw, carKw)
                    raisedStop = previous.stopIndex
                    continue
                }

                // %80 bir sonraki istasyona yetmiyorsa önceki durakta daha fazla şarj edilir.
                val last = stops.lastOrNull()
                val lastDeparture = last?.departurePercent ?: 100.0
                if (last != null && raisedStop != last.stopIndex && lastDeparture < 100.0) {
                    val newDeparture = min(100.0, ceil(needPct(last.distanceFromOriginKm, last.detourDistanceKm) - 1e-9))
                    if (newDeparture > lastDeparture) {
                        energy += battery * (newDeparture - lastDeparture) / 100
                        val lastStationKw = last.station.dcKwFor(connector) ?: (last.chargeKw ?: carKw)
                        stops[stops.lastIndex] = withCharge(last, battery, newDeparture, lastStationKw, carKw)
                        raisedStop = last.stopIndex
                        continue
                    }
                }
                warning = when {
                    energy - eMin >= kwh(total - alongKm) - 1e-9 ->
                        "Varışta istediğin şarj seviyesi tutmuyor; varınca şarj etmen gerekebilir."
                    candidates.isEmpty() ->
                        "Bu güzergahta aracına uygun (${connector.label}) DC şarj istasyonu bulunamadı."
                    else -> {
                        val firstBeyond = candidates.firstOrNull { it.alongKm > alongKm + minLeg }
                        val neededStart = firstBeyond?.let {
                            ceil((kwh(it.alongKm + it.crowDetourKm) + eMin) / battery * 100 - 1e-9).toInt()
                        }
                        if (lowStart) {
                            "Bataryan çok az ve ulaşabileceğin mesafede aracına uygun şarj istasyonu yok. " +
                                "Önce en yakın şarj noktasına git."
                        } else if (stops.isEmpty() && firstBeyond != null && neededStart != null && neededStart <= 100) {
                            "Yola en az %$neededStart şarjla çık: ilk uygun şarj istasyonu " +
                                "${firstBeyond.alongKm.toInt()}. km'de."
                        } else {
                            "Kalan ${((energy - eMin) / kwhPerKm).toInt().coerceAtLeast(0)} km menzil içinde " +
                                "aracına uygun şarj istasyonu yok."
                        }
                    }
                }
                break
            }

            val fromKm = alongKm
            val farthest = reachable.maxOf { it.alongKm }
            val pool = reachable.filter { it.alongKm >= fromKm + POOL_FRACTION * (farthest - fromKm) }
            val forced = reachable.firstOrNull { it.station.id == forcedStationIds[stops.size + 1] }

            fun score(candidate: Candidate, extraKm: Double, extraMinutes: Double?): Double {
                val arrivalPct = arrivalPercent(energy, candidate.alongKm - fromKm + extraKm / 2)
                val target = departureFor(candidate.alongKm, extraKm / 2, arrivalPct)
                return (extraMinutes ?: (extraKm / 50.0 * 60.0)) +
                    chargeMinutes(battery, arrivalPct, max(arrivalPct, target), candidate.stationKw, carKw) +
                    (farthest - candidate.alongKm) * EARLY_STOP_MIN_PER_KM +
                    max(0, stopsAfter(candidate.alongKm) - stopsAfter(farthest)) * EXTRA_STOP_MIN
            }

            val shortlist = pool
                .sortedBy { score(it, it.crowDetourKm * 2, null) }
                .take(SHORTLIST_SIZE)
                .let { if (forced == null || forced in it) it else it + forced }

            val crowScored = shortlist.map { Scored(it, it.crowDetourKm * 2, null, score(it, it.crowDetourKm * 2, null)) }
            val roadScored = detourResolver
                ?.let { resolveRoadDetours(it, routePoints, fromKm, total, shortlist.map { c -> c.alongKm to c.station.location }) }
                ?.let { detours ->
                    shortlist.mapIndexedNotNull { index, candidate ->
                        // Yol verisi geldiyse ama bu istasyon için yoksa, yol ağından ulaşılamıyor demektir.
                        val road = detours.getOrNull(index) ?: return@mapIndexedNotNull null
                        val reach = kwh(candidate.alongKm - fromKm + road.extraKm / 2)
                        if (reach > energy - floor + 1e-9) return@mapIndexedNotNull null
                        Scored(candidate, road.extraKm, road.extraMinutes, score(candidate, road.extraKm, road.extraMinutes))
                    }
                }
                ?.takeIf { it.isNotEmpty() }
            val scored = roadScored ?: crowScored

            val chosen = scored.firstOrNull { it.candidate.station.id == forced?.station?.id }
                ?: scored.minBy { it.score }
            val candidate = chosen.candidate
            val oneWayDetour = chosen.extraKm / 2
            val arrival = energy - kwh(candidate.alongKm - fromKm + oneWayDetour)
            val arrivalPct = arrival / battery * 100
            val departure = max(ceil(departureFor(candidate.alongKm, oneWayDetour, arrivalPct) - 1e-9), arrivalPct)

            val alternatives = scored
                .filter {
                    it.candidate.station.id != candidate.station.id &&
                        GeoUtils.distanceKm(it.candidate.station.location, candidate.station.location) > 0.3
                }
                .sortedBy { it.score }
                .take(MAX_ALTERNATIVES)
                .map { StopAlternative(it.candidate.station, it.candidate.alongKm, it.extraKm / 2, it.extraMinutes) }

            val stop = FuelStop(
                stopIndex = stops.size + 1,
                station = candidate.station,
                distanceFromOriginKm = candidate.alongKm,
                legDistanceKm = candidate.alongKm - fromKm,
                arrivalFuelLevelPercent = arrivalPct,
                arrivalFuelLiters = arrival,
                refuelLiters = 0.0,
                estimatedRefuelCostTL = 0.0,
                detourDistanceKm = oneWayDetour,
                alternatives = alternatives,
                detourMinutes = chosen.extraMinutes,
                pricePerLiterTL = candidate.station.kwhPriceTL ?: defaultPrice,
                chargeKw = min(candidate.stationKw, carKw)
            )
            stops.add(withCharge(stop, battery, departure, candidate.stationKw, carKw))

            alongKm = candidate.alongKm
            // Bataryadan çıkılır, rotaya dönüş yolu hemen düşülür.
            energy = battery * departure / 100 - kwh(oneWayDetour)
        }

        // Durak sınırına takılıp varışa ulaşamadan biten plan sessizce "tamam" görünmesin.
        if (warning == null && energy - eDest < kwh(total - alongKm) - 1e-9) {
            warning = "Rota bu araç için çok uzun: plan ${stops.size} şarj durağından sonra kesildi. " +
                "Yolculuğu ara bir varış noktasıyla ikiye böl."
        }

        val drivenKm = total + stops.sumOf { it.extraRoadKm }
        val consumed = kwh(drivenKm)
        val drivingMinutes = routeDurationMinutes ?: (total / 92.0 * 60)
        val stopMinutes = stops.sumOf {
            (it.chargeMinutes ?: 0.0) + STOP_OVERHEAD_MIN + (it.detourMinutes ?: (it.extraRoadKm / 50.0 * 60.0))
        }

        return TripPlanResult(
            origin = origin,
            destination = destination,
            totalDistanceKm = total,
            totalDrivenDistanceKm = drivenKm,
            estimatedDrivingTimeMinutes = (drivingMinutes + stopMinutes).toInt(),
            stops = stops,
            totalFuelConsumedLiters = consumed,
            totalEstimatedCostTL = consumed / CHARGE_EFFICIENCY * defaultPrice,
            totalRefuelCostTL = stops.sumOf { it.estimatedRefuelCostTL },
            arrivalFuelLiters = max(0.0, energy - kwh(total - alongKm)),
            routePoints = routePoints,
            vehicleProfile = vehicleProfile,
            preferredBrands = emptySet(),
            fuelPrice = fuelPrice,
            warning = warning,
            routeDurationMinutes = routeDurationMinutes
        )
    }

    /** Durağın çıkış yüzdesini ayarlar; eklenen kWh, süre ve tutar buna göre yeniden hesaplanır. */
    private fun withCharge(
        stop: FuelStop,
        battery: Double,
        departurePercent: Double,
        stationKw: Double,
        carKw: Double
    ): FuelStop {
        val added = battery * (departurePercent - stop.arrivalFuelLevelPercent) / 100
        return stop.copy(
            departurePercent = departurePercent,
            refuelLiters = added,
            chargeMinutes = chargeMinutes(battery, stop.arrivalFuelLevelPercent, departurePercent, stationKw, carKw),
            // Şebekeden çekilen enerji bataryaya girenden ~%5 fazladır; ücret şebekeden ölçülür.
            estimatedRefuelCostTL = added / CHARGE_EFFICIENCY * stop.pricePerLiterTL
        )
    }

    internal companion object {
        const val MAX_CHARGE_PERCENT = 80.0
        /** Son durakta, varışa yetecekse çıkılabilecek en yüksek yüzde. */
        const val FINISH_CHARGE_PERCENT = 90.0
        const val STOP_OVERHEAD_MIN = 5.0
        const val EXTRA_STOP_MIN = 20.0
        const val EARLY_STOP_MIN_PER_KM = 0.05
        const val POOL_FRACTION = 0.5
        const val CHARGE_EFFICIENCY = 0.95
        const val MAX_DETOUR_KM = 6.0
        const val MIN_LEG_KM = 5.0
        /** Bu kadar km'lik menzilden azı kalmışsa başlangıç "düşük" sayılır (bkz. [HARD_MIN_PERCENT]). */
        const val LOW_START_KM = 15.0
        /** Düşük başlangıçta ilk istasyona varışta kabul edilen en düşük şarj. */
        const val HARD_MIN_PERCENT = 3.0
        /** Bu kadar yakın iki durak, öncekini uzatıp birleştirilir. */
        const val MERGE_STOPS_KM = 25.0
        /** Küçük bataryalı araçla ülkeyi boydan boya geçen rota (ör. Edirne-Kars) 15+ durak ister. */
        const val MAX_STOPS = 30
        const val SHORTLIST_SIZE = 24
        const val MAX_ALTERNATIVES = 3

        /**
         * DC şarj gücünün doluluğa göre düşüşü (Open EV Data'nın varsayılan eğrisi):
         * %50'ye kadar tam güç, %80'de yarısı, %100'de onda biri.
         * ponytail: tek eğri tüm araçlara uygulanır; araç bazlı eğri verisi bulunursa buraya bağlanmalı.
         */
        fun curve(soc: Double): Double = when {
            soc <= 50 -> 1.0
            soc <= 80 -> 1.0 - 0.5 * (soc - 50) / 30
            else -> 0.5 - 0.4 * (soc - 80) / 20
        }

        /**
         * [fromPct]'ten [toPct]'e şarj süresi (dk); %1'lik adımlarla toplanır. Her adımda güç,
         * istasyonun sınırı ile aracın doluluğa göre düşen kabul gücünün küçüğüdür: 150 kW'lık
         * araç 50 kW'lık istasyonda %80'e kadar neredeyse hep 50 kW çeker.
         */
        fun chargeMinutes(
            batteryKwh: Double,
            fromPct: Double,
            toPct: Double,
            stationKw: Double,
            carKw: Double = stationKw
        ): Double {
            var minutes = 0.0
            var soc = fromPct
            while (soc < toPct - 1e-9) {
                val step = min(1.0, toPct - soc)
                val kw = min(stationKw, carKw * curve(soc + step / 2)).coerceAtLeast(1.0)
                minutes += batteryKwh * step / 100 / kw * 60
                soc += step
            }
            return minutes
        }
    }
}
