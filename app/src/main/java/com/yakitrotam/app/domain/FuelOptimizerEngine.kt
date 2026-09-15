package com.yakitrotam.app.domain

import com.yakitrotam.app.data.model.*
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.util.GeoUtils

class FuelOptimizerEngine(
    private val stationRepository: GasStationRepository
) {

    /**
     * Rota üzerindeki araç tüketimini simüle eder ve optimum yakıt duraklarını hesaplar.
     */
    fun calculateTripPlan(
        origin: CityLocation,
        destination: CityLocation,
        routePoints: List<LatLng>,
        vehicleProfile: VehicleProfile,
        preferredBrands: Set<FuelBrand> = emptySet()
    ): TripPlanResult {
        val totalDistanceKm = GeoUtils.totalPolylineDistanceKm(routePoints)
        val stops = mutableListOf<FuelStop>()

        val consumptionRate = vehicleProfile.consumptionPer100Km.coerceAtLeast(1.0)
        val tankCapacity = vehicleProfile.tankCapacityLiters.coerceAtLeast(10.0)
        val reserveLiters = vehicleProfile.reserveLiters
        val fuelPricePerLiter = vehicleProfile.fuelType.averagePricePerLiterTL

        var currentDistanceAlongRoute = 0.0
        var currentFuelLiters = vehicleProfile.currentFuelLiters
        var stopIndex = 1

        val maxIterations = 20
        var iteration = 0

        while (iteration < maxIterations) {
            iteration++
            val remainingRouteDist = totalDistanceKm - currentDistanceAlongRoute
            val safeUsableFuel = (currentFuelLiters - reserveLiters).coerceAtLeast(0.0)
            val currentSafeRangeKm = (safeUsableFuel / consumptionRate) * 100.0

            // Mevcut yakıt varış noktasına rezerve düşmeden yetiyor mu?
            val fuelNeededToFinish = (remainingRouteDist / 100.0) * consumptionRate
            if (currentFuelLiters - fuelNeededToFinish >= reserveLiters) {
                // Yakıt güvenli şekilde varış noktasına yetiyor, başka durağa gerek yok
                break
            }

            // Yakıt yetmiyor; durak arama penceresi:
            // Güvenli menzilin %80 ile %95'i arasına denk gelen nokta hedeflenir
            val optimalDistanceToRefuel = (currentSafeRangeKm * 0.88)
                .coerceAtLeast(30.0)
                .coerceAtMost(remainingRouteDist - 10.0)

            val targetSearchDistance = currentDistanceAlongRoute + optimalDistanceToRefuel
            val targetCoordinate = GeoUtils.findPointAtDistance(routePoints, targetSearchDistance)

            // Tercih edilen markalardan aday istasyon ara
            var candidates = stationRepository.findCandidateStationsAlongCorridor(
                searchCenter = targetCoordinate,
                routeSegment = routePoints,
                fuelType = vehicleProfile.fuelType,
                preferredBrands = preferredBrands,
                maxSearchRadiusKm = (currentSafeRangeKm * 0.35).coerceIn(35.0, 90.0),
                maxDetourFromRouteKm = 12.0
            )

            // Eğer kullanıcının seçtiği özel markalarda o bölgede istasyon bulunamadıysa,
            // sürücünün yolda kalmaması için herhangi bir güvenilir istasyonu fallback olarak ara
            if (candidates.isEmpty() && preferredBrands.isNotEmpty()) {
                candidates = stationRepository.findCandidateStationsAlongCorridor(
                    searchCenter = targetCoordinate,
                    routeSegment = routePoints,
                    fuelType = vehicleProfile.fuelType,
                    preferredBrands = emptySet(), // tüm markalar
                    maxSearchRadiusKm = (currentSafeRangeKm * 0.40).coerceIn(45.0, 110.0),
                    maxDetourFromRouteKm = 15.0
                )
            }

            // Adaylardan daha önce eklenmemiş olanı seç
            val chosenCandidate = candidates.firstOrNull { candidate ->
                stops.none { it.station.id == candidate.first.id }
            }

            if (chosenCandidate != null) {
                val station = chosenCandidate.first
                val detourKm = GeoUtils.minDistanceToRouteKm(station.location, routePoints)

                // İstasyonun rotanın başlangıcına göre yaklaşık kümülatif mesafesini tahmin et
                val stationAlongRouteDist = estimateStationDistanceAlongRoute(
                    station.location,
                    routePoints,
                    currentDistanceAlongRoute
                )

                val legDist = (stationAlongRouteDist - currentDistanceAlongRoute).coerceAtLeast(15.0)
                val fuelBurnedInLeg = (legDist / 100.0) * consumptionRate
                val arrivalFuel = (currentFuelLiters - fuelBurnedInLeg).coerceAtLeast(0.5)
                val arrivalPercent = (arrivalFuel / tankCapacity) * 100.0
                val refuelLiters = (tankCapacity - arrivalFuel).coerceAtLeast(1.0)
                val costTL = refuelLiters * fuelPricePerLiter

                val stop = FuelStop(
                    stopIndex = stopIndex++,
                    station = station,
                    distanceFromOriginKm = stationAlongRouteDist,
                    legDistanceKm = legDist,
                    arrivalFuelLevelPercent = arrivalPercent,
                    arrivalFuelLiters = arrivalFuel,
                    refuelLiters = refuelLiters,
                    estimatedRefuelCostTL = costTL,
                    detourDistanceKm = detourKm
                )
                stops.add(stop)

                // Depoyu tam doldurmuş olarak yolculuğa devam et
                currentDistanceAlongRoute = stationAlongRouteDist
                currentFuelLiters = tankCapacity
            } else {
                // İstasyon bulunamadıysa zorunlu ilerleme yap ve döngüden çık
                break
            }
        }

        val totalFuelConsumed = (totalDistanceKm / 100.0) * consumptionRate
        val totalCostTL = totalFuelConsumed * fuelPricePerLiter
        // Ortalama 95 km/s hız + her yakıt molası için 15 dakika mola
        val drivingMinutes = ((totalDistanceKm / 92.0) * 60).toInt() + (stops.size * 15)

        return TripPlanResult(
            origin = origin,
            destination = destination,
            totalDistanceKm = totalDistanceKm,
            estimatedDrivingTimeMinutes = drivingMinutes,
            stops = stops,
            totalFuelConsumedLiters = totalFuelConsumed,
            totalEstimatedCostTL = totalCostTL,
            routePoints = routePoints,
            vehicleProfile = vehicleProfile,
            preferredBrands = preferredBrands
        )
    }

    /**
     * Verilen bir istasyon koordinatının rota çizgisi üzerindeki izdüşüm mesafesini yaklaşık olarak hesaplar.
     */
    private fun estimateStationDistanceAlongRoute(
        stationLocation: LatLng,
        routePoints: List<LatLng>,
        minDistanceKm: Double
    ): Double {
        if (routePoints.size < 2) return minDistanceKm

        var closestDistance = Double.MAX_VALUE
        var bestAccumulatedKm = minDistanceKm
        var accumulated = 0.0

        for (i in 0 until routePoints.size - 1) {
            val p1 = routePoints[i]
            val p2 = routePoints[i + 1]
            val segDist = GeoUtils.distanceKm(p1, p2)
            val d = GeoUtils.distanceToSegmentKm(stationLocation, p1, p2)
            if (d < closestDistance) {
                closestDistance = d
                bestAccumulatedKm = accumulated
            }
            accumulated += segDist
        }

        return bestAccumulatedKm.coerceAtLeast(minDistanceKm + 10.0)
    }
}
