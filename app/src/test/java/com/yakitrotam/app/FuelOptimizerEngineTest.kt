package com.yakitrotam.app

import com.yakitrotam.app.data.model.*
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.domain.FuelOptimizerEngine
import com.yakitrotam.app.util.GeoUtils
import org.junit.Assert.*
import org.junit.Test

class FuelOptimizerEngineTest {

    private val istanbul = CityLocation("İstanbul", "İstanbul", 40.99, 29.02)
    private val ankara = CityLocation("Ankara", "Ankara", 39.92, 32.85)

    /** İstanbul -> Ankara O-4 aksını kabaca izleyen rota. */
    private val istanbulAnkaraRoute = listOf(
        istanbul.latLng,
        LatLng(40.80, 29.43),
        LatLng(40.77, 29.98),
        LatLng(40.78, 30.76),
        LatLng(40.82, 31.14),
        LatLng(40.75, 31.48),
        LatLng(40.78, 32.25),
        ankara.latLng
    )

    private fun station(
        id: String,
        brand: FuelBrand,
        lat: Double,
        lng: Double,
        lpg: Boolean? = true
    ) = GasStation(
        id = id,
        name = "$id istasyonu",
        brand = brand,
        latitude = lat,
        longitude = lng,
        hasLpg = lpg,
        hasDiesel = true,
        hasGasoline = true
    )

    private fun engineWith(stations: List<GasStation>) =
        FuelOptimizerEngine(GasStationRepository(initialStations = stations))

    private val corridorStations = listOf(
        station("gebze_shell", FuelBrand.SHELL, 40.801, 29.431),
        station("izmit_opet", FuelBrand.OPET, 40.772, 29.981),
        station("hendek_po", FuelBrand.PETROL_OFISI, 40.781, 30.762, lpg = false),
        station("duzce_opet", FuelBrand.OPET, 40.821, 31.141),
        station("bolu_shell", FuelBrand.SHELL, 40.752, 31.482),
        station("gerede_shell", FuelBrand.SHELL, 40.781, 32.251)
    )

    @Test
    fun `dolu depoyla kisa yolculuk durak gerektirmez`() {
        val destination = CityLocation("Gebze", "Kocaeli", 40.80, 29.43)
        val car = VehicleProfile(
            consumptionPer100Km = 7.0,
            tankCapacityLiters = 50.0,
            currentLevelPercent = 100.0,
            reserveThresholdPercent = 15.0
        )

        val result = engineWith(corridorStations).calculateTripPlan(
            origin = istanbul,
            destination = destination,
            routePoints = listOf(istanbul.latLng, destination.latLng),
            vehicleProfile = car
        )

        assertEquals(0, result.stopsCount)
        assertNull(result.warning)
        assertTrue("Varışta yakıt kalmalı", result.arrivalFuelLiters > 0)
    }

    @Test
    fun `secilen her durak mevcut yakitla gercekten ulasilabilir olmali`() {
        val car = VehicleProfile(
            consumptionPer100Km = 7.0,
            tankCapacityLiters = 50.0,
            currentLevelPercent = 25.0,
            reserveThresholdPercent = 15.0
        )

        val result = engineWith(corridorStations).calculateTripPlan(
            origin = istanbul,
            destination = ankara,
            routePoints = istanbulAnkaraRoute,
            vehicleProfile = car
        )

        assertTrue("En az bir durak üretilmeli", result.hasStops)

        val tank = car.tankCapacityLiters
        var fuel = car.currentFuelLiters
        var along = 0.0
        result.stops.forEachIndexed { index, stop ->
            assertTrue(
                "Duraklar rota boyunca ileri gitmeli",
                stop.distanceFromOriginKm > along
            )
            val burned = (stop.legDistanceKm + stop.detourDistanceKm) / 100.0 * car.consumptionPer100Km
            assertTrue(
                "${stop.station.id} durağına yakıt yetmiyor: ${fuel - burned} L",
                fuel - burned >= -0.001
            )
            assertEquals(fuel - burned, stop.arrivalFuelLiters, 0.01)

            if (index < result.stops.lastIndex) {
                // Ara duraklarda depo tam doldurulur.
                assertEquals(tank - stop.arrivalFuelLiters, stop.refuelLiters, 0.01)
            } else {
                // Son durakta sadece varışa yetecek kadar alınır.
                assertTrue(stop.refuelLiters > 0.0)
                assertTrue(stop.refuelLiters <= tank - stop.arrivalFuelLiters + 0.01)
            }

            along = stop.distanceFromOriginKm
            fuel = stop.arrivalFuelLiters + stop.refuelLiters -
                stop.detourDistanceKm / 100.0 * car.consumptionPer100Km
        }

        // Son duraktan sonra varışa yakıt yetmeli (rezerv payıyla).
        val remaining = (result.totalDistanceKm - along) / 100.0 * car.consumptionPer100Km
        assertTrue("Varışa yakıt yetmiyor", fuel - remaining >= -0.01)
    }

    @Test
    fun `menzil disinda tek istasyon varsa durak konulmaz ve uyari verilir`() {
        // Tek istasyon rotanın 480. km'sinde; depoda sadece ~70 km'lik kullanılabilir yakıt var.
        val farStation = station("gerede_shell", FuelBrand.SHELL, 40.781, 32.251)
        val car = VehicleProfile(
            consumptionPer100Km = 7.0,
            tankCapacityLiters = 50.0,
            currentLevelPercent = 25.0,
            reserveThresholdPercent = 15.0
        )

        val result = engineWith(listOf(farStation)).calculateTripPlan(
            origin = istanbul,
            destination = ankara,
            routePoints = istanbulAnkaraRoute,
            vehicleProfile = car
        )

        assertEquals("Ulaşılamayan istasyon durak olarak seçilmemeli", 0, result.stopsCount)
        assertNotNull("Kullanıcı uyarılmalı", result.warning)
    }

    @Test
    fun `tercih edilen marka menzildeyse secilir`() {
        val car = VehicleProfile(
            consumptionPer100Km = 7.0,
            tankCapacityLiters = 50.0,
            currentLevelPercent = 25.0,
            reserveThresholdPercent = 15.0
        )

        val result = engineWith(corridorStations).calculateTripPlan(
            origin = istanbul,
            destination = ankara,
            routePoints = istanbulAnkaraRoute,
            vehicleProfile = car,
            preferredBrands = setOf(FuelBrand.SHELL)
        )

        assertTrue(result.hasStops)
        assertEquals(FuelBrand.SHELL, result.stops.first().station.brand)
    }

    @Test
    fun `maliyet canli fiyat anlik goruntusundan hesaplanir`() {
        val price = FuelPriceSnapshot(
            pricesPerLiterTL = mapOf(FuelType.BENZIN to 80.16),
            sourceName = "test",
            sourceUrl = "",
            regionName = "İSTANBUL",
            priceDate = "16.09.2026",
            fetchedAtEpochMillis = 1L
        )
        val car = VehicleProfile(
            consumptionPer100Km = 7.0,
            tankCapacityLiters = 50.0,
            currentLevelPercent = 25.0,
            reserveThresholdPercent = 15.0
        )

        val result = engineWith(corridorStations).calculateTripPlan(
            origin = istanbul,
            destination = ankara,
            routePoints = istanbulAnkaraRoute,
            vehicleProfile = car,
            preferredBrands = emptySet(),
            fuelPrice = price
        )

        assertEquals(
            result.totalFuelConsumedLiters * 80.16,
            result.totalEstimatedCostTL,
            0.01
        )
        assertEquals(
            result.stops.sumOf { it.estimatedRefuelCostTL },
            result.totalRefuelCostTL,
            0.01
        )
        // Yol boyunca sürülen mesafe ana güzergahtan kısa olamaz.
        assertTrue(result.totalDrivenDistanceKm >= result.totalDistanceKm)
    }

    @Test
    fun `lpg desteklemeyen istasyon lpg araclara onerilmez`() {
        val repo = GasStationRepository(initialStations = corridorStations)
        val filtered = repo.filterStations(FuelType.LPG)

        assertFalse(filtered.any { it.id == "hendek_po" })
        assertTrue(filtered.all { it.hasLpg != false })
    }

    @Test
    fun `rota izdusumu istasyonun km konumunu ve sapmasini dogru verir`() {
        val route = listOf(LatLng(40.0, 30.0), LatLng(40.0, 32.0))
        val cumulative = GeoUtils.cumulativeDistancesKm(route)
        val total = cumulative.last()

        // Tam orta noktanın ~5 km kuzeyindeki istasyon
        val projection = GeoUtils.projectOntoRoute(LatLng(40.045, 31.0), route, cumulative)

        assertEquals(total / 2.0, projection.alongKm, total * 0.02)
        assertEquals(5.0, projection.detourKm, 0.6)
    }
    /** İstasyon kimliğine göre sabit ek yol döndüren sahte yol ağı. */
    private fun fakeRoads(extraKmByStationId: Map<String, Double>, stations: List<GasStation>) =
        DetourResolver { _, _, points ->
            points.map { point ->
                val station = stations.first { it.location == point }
                extraKmByStationId[station.id]?.let { RoadDetour(extraKm = it, extraMinutes = it * 1.2) }
            }
        }

    private val lowFuelCar = VehicleProfile(
        consumptionPer100Km = 7.0,
        tankCapacityLiters = 50.0,
        currentLevelPercent = 25.0,
        reserveThresholdPercent = 15.0
    )

    @Test
    fun `yola yakin gorunen ama otoyoldan cikis gerektiren istasyon secilmez`() {
        // İkisi de Gebze civarında. "yakin" rotaya kuş uçuşu neredeyse sıfır mesafede ama
        // gerçekte 18 km ek yol istiyor; "tesis" biraz daha uzakta görünüyor ama yol üstünde.
        val stations = listOf(
            station("yakin_ama_cikis", FuelBrand.SHELL, 40.8002, 29.4302),
            station("yol_ustu_tesis", FuelBrand.OPET, 40.8040, 29.4330)
        )
        val roads = fakeRoads(mapOf("yakin_ama_cikis" to 18.0, "yol_ustu_tesis" to 0.2), stations)

        val plan = engineWith(stations).calculateTripPlan(
            istanbul, ankara, istanbulAnkaraRoute, lowFuelCar, detourResolver = roads
        )

        val first = plan.stops.first()
        assertEquals("yol_ustu_tesis", first.station.id)
        assertEquals(0.2, first.extraRoadKm, 0.001)
        assertNotNull(first.detourMinutes)
    }

    @Test
    fun `penceredeki tum adaylar buyuk sapma gerektiriyorsa daha erken yol ustu istasyon secilir`() {
        // Menzil ~71 km. Uçtaki istasyon (İzmit öncesi) 20 km sapma istiyor;
        // yolun ortasındaki Gebze tesisi ise yol üstünde.
        val stations = listOf(
            station("gebze_tesis", FuelBrand.OPET, 40.801, 29.431),
            station("uzak_cikis", FuelBrand.SHELL, 40.781, 29.80)
        )
        val roads = fakeRoads(mapOf("gebze_tesis" to 0.1, "uzak_cikis" to 20.0), stations)

        val plan = engineWith(stations).calculateTripPlan(
            istanbul, ankara, istanbulAnkaraRoute, lowFuelCar, detourResolver = roads
        )

        assertEquals("gebze_tesis", plan.stops.first().station.id)
    }

    @Test
    fun `yol servisi cevap vermezse kus ucusu tahmine geri dusulur`() {
        val offline = DetourResolver { _, _, _ -> null }

        val withResolver = engineWith(corridorStations).calculateTripPlan(
            istanbul, ankara, istanbulAnkaraRoute, lowFuelCar, detourResolver = offline
        )
        val without = engineWith(corridorStations).calculateTripPlan(
            istanbul, ankara, istanbulAnkaraRoute, lowFuelCar
        )

        assertEquals(without.stops.map { it.station.id }, withResolver.stops.map { it.station.id })
        assertNull(withResolver.stops.first().detourMinutes)
    }

    @Test
    fun `her durak kendi ilinin fiyatiyla hesaplanir`() {
        val plan = engineWith(corridorStations).calculateTripPlan(
            istanbul, ankara, istanbulAnkaraRoute, lowFuelCar,
            // Doğuya gittikçe pahalanan uydurma fiyat: boylam × 2.
            stopPricePerLiter = { it.longitude * 2 }
        )

        assertTrue(plan.stops.isNotEmpty())
        plan.stops.forEach { stop ->
            assertEquals(stop.station.longitude * 2, stop.pricePerLiterTL, 1e-9)
            assertEquals(stop.refuelLiters * stop.pricePerLiterTL, stop.estimatedRefuelCostTL, 1e-6)
        }
    }

    @Test
    fun `varista yarim depo istenirse son dolum buna gore buyur`() {
        val car = lowFuelCar.copy(currentLevelPercent = 60.0)
        val reserveOnly = engineWith(corridorStations).calculateTripPlan(istanbul, ankara, istanbulAnkaraRoute, car)
        val halfTank = engineWith(corridorStations).calculateTripPlan(
            istanbul, ankara, istanbulAnkaraRoute, car.copy(arrivalFuel = ArrivalFuel.HALF)
        )

        assertTrue("Rezervle varış yarım deponun altında kalmalı", reserveOnly.arrivalFuelPercent < 50.0)
        assertNull(halfTank.warning)
        assertTrue("Varışta en az yarım depo olmalı: %${halfTank.arrivalFuelPercent}", halfTank.arrivalFuelPercent >= 50.0)
        assertTrue(halfTank.totalRefuelCostTL > reserveOnly.totalRefuelCostTL)
    }

    @Test
    fun `yakit yetiyorsa ama varis hedefi tutmuyorsa yolda kalma uyarisi verilmez`() {
        // Yakıt varışa rezervle yetiyor; yarım depo hedefi için yol üstünde hiç istasyon yok.
        val car = lowFuelCar.copy(currentLevelPercent = 100.0, arrivalFuel = ArrivalFuel.HALF)
        val plan = engineWith(emptyList()).calculateTripPlan(istanbul, ankara, istanbulAnkaraRoute, car)

        assertEquals(0, plan.stopsCount)
        assertTrue(plan.warning.orEmpty().contains("varışta depo hedefin"))
    }

    @Test
    fun `adsiz istasyonlar alternatiflerde en sona kalir`() {
        val unnamed = station("adsiz", FuelBrand.DIGER, 40.8035, 29.4325).copy(name = GasStation.UNNAMED)
        val stations = listOf(
            station("secilen", FuelBrand.OPET, 40.8040, 29.4330),
            unnamed,
            station("uzak_a", FuelBrand.SHELL, 40.8080, 29.4200),
            station("uzak_b", FuelBrand.BP, 40.8090, 29.4100),
            station("uzak_c", FuelBrand.AYTEMIZ, 40.8100, 29.4000)
        )
        // Adsız istasyon puanca en iyi alternatif olsa da adı olanlar önce gelmeli.
        val roads = fakeRoads(
            mapOf("secilen" to 0.0, "adsiz" to 0.1, "uzak_a" to 2.0, "uzak_b" to 2.5, "uzak_c" to 2.8), stations
        )

        val plan = engineWith(stations).calculateTripPlan(
            istanbul, ankara, istanbulAnkaraRoute, lowFuelCar, detourResolver = roads
        )

        val alternatives = plan.stops.first().alternatives.map { it.station.id }
        assertEquals("secilen", plan.stops.first().station.id)
        assertEquals(3, alternatives.size)
        assertFalse("Yeterince adı olan aday varken adsız önerilmemeli: $alternatives", "adsiz" in alternatives)
    }

    @Test
    fun `sure yol servisinden gelirse sabit hiz tahmini kullanilmaz`() {
        val car = lowFuelCar.copy(currentLevelPercent = 100.0)
        val plan = engineWith(emptyList()).calculateTripPlan(
            istanbul, CityLocation("Gebze", "Kocaeli", 40.80, 29.43),
            listOf(istanbul.latLng, LatLng(40.80, 29.43)), car, routeDurationMinutes = 61.0
        )

        assertEquals(61, plan.estimatedDrivingTimeMinutes)
    }
}
