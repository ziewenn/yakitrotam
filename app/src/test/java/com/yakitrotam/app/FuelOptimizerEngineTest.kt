package com.yakitrotam.app

import com.yakitrotam.app.data.model.*
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.domain.FuelOptimizerEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FuelOptimizerEngineTest {

    private lateinit var mockStations: List<GasStation>
    private lateinit var stationRepo: GasStationRepository
    private lateinit var optimizerEngine: FuelOptimizerEngine

    @Before
    fun setUp() {
        mockStations = listOf(
            GasStation(
                id = "station_shell_kocaeli",
                name = "Shell Kocaeli Körfez Tesisleri",
                brand = FuelBrand.SHELL,
                latitude = 40.77,
                longitude = 29.74,
                hasLpg = true,
                hasDiesel = true,
                hasGasoline = true
            ),
            GasStation(
                id = "station_shell_1",
                name = "Shell Otoyol Tesisi Bolu",
                brand = FuelBrand.SHELL,
                latitude = 40.75,
                longitude = 31.48,
                hasLpg = true,
                hasDiesel = true,
                hasGasoline = true
            ),
            GasStation(
                id = "station_opet_1",
                name = "Opet Düzce Hizmet Alanı",
                brand = FuelBrand.OPET,
                latitude = 40.82,
                longitude = 31.14,
                hasLpg = true,
                hasDiesel = true,
                hasGasoline = true
            ),
            GasStation(
                id = "station_po_1",
                name = "Petrol Ofisi Hendek",
                brand = FuelBrand.PETROL_OFISI,
                latitude = 40.78,
                longitude = 30.76,
                hasLpg = false,
                hasDiesel = true,
                hasGasoline = true
            )
        )

        stationRepo = GasStationRepository(null, mockStations)
        optimizerEngine = FuelOptimizerEngine(stationRepo)
    }

    @Test
    fun testShortTrip_FullTank_RequiresZeroStops() {
        val origin = CityLocation("İstanbul", "İstanbul", 40.99, 29.02)
        val destination = CityLocation("Gebze", "Kocaeli", 40.80, 29.43)
        val routePoints = listOf(origin.latLng, destination.latLng)

        // 50 litrelik dolu depo (%100), 50 km yol için durak gerekmez
        val car = VehicleProfile(
            fuelType = FuelType.BENZIN,
            consumptionPer100Km = 7.0,
            tankCapacityLiters = 50.0,
            currentLevelPercent = 100.0,
            reserveThresholdPercent = 15.0
        )

        val result = optimizerEngine.calculateTripPlan(
            origin = origin,
            destination = destination,
            routePoints = routePoints,
            vehicleProfile = car
        )

        assertEquals("Kısa rota ve dolu depo için durak sayısı 0 olmalıdır", 0, result.stopsCount)
        assertFalse(result.hasStops)
    }

    @Test
    fun testLongTrip_LowFuel_GeneratesStopsWithPreferredBrand() {
        val origin = CityLocation("İstanbul", "İstanbul", 40.99, 29.02)
        val destination = CityLocation("Ankara", "Ankara", 39.92, 32.85)

        // İstanbul -> Bolu -> Ankara temsili rotası
        val routePoints = listOf(
            origin.latLng,
            LatLng(40.78, 30.76),
            LatLng(40.82, 31.14),
            LatLng(40.75, 31.48),
            destination.latLng
        )

        // Depo %25 dolu (~12.5L), 7L/100km tüketimle ~70 km sonra rezerve düşer
        val car = VehicleProfile(
            fuelType = FuelType.BENZIN,
            consumptionPer100Km = 7.0,
            tankCapacityLiters = 50.0,
            currentLevelPercent = 25.0,
            reserveThresholdPercent = 15.0
        )

        // Kullanıcı SADECE Shell tercih etti
        val result = optimizerEngine.calculateTripPlan(
            origin = origin,
            destination = destination,
            routePoints = routePoints,
            vehicleProfile = car,
            preferredBrands = setOf(FuelBrand.SHELL)
        )

        assertTrue("Yolculuk için durak üretilmiş olmalıdır", result.stopsCount >= 1)
        val firstStop = result.stops.first()
        assertEquals("Tercih edilen marka Shell olmalıdır", FuelBrand.SHELL, firstStop.station.brand)
        assertTrue("Refuel miktarı pozitif olmalıdır", firstStop.refuelLiters > 0)
    }

    @Test
    fun testLpgFilter_ExcludesNonLpgStations() {
        val filtered = stationRepo.filterStations(FuelType.LPG)
        // Petrol Ofisi istasyonunda hasLpg = false tanımlanmıştı
        assertTrue(filtered.all { it.hasLpg })
        assertFalse(filtered.any { it.id == "station_po_1" })
    }
}
