package com.yakitrotam.app

import com.yakitrotam.app.data.model.*
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.domain.EvChargePlanner
import org.junit.Assert.*
import org.junit.Test

/**
 * Beklenen değerler, planlama kuralının bağımsız Python kopyasından (araştırma notlarındaki evspec.py)
 * alınmıştır. Rota kuzeye doğru düz bir çizgi; istasyonlar tam rota üstünde (sapma yok).
 */
class EvChargePlannerTest {

    private fun at(km: Double) = LatLng(39.0 + km / 111.195, 30.0)

    private fun route(totalKm: Int) = (0..totalKm step 5).map { at(it.toDouble()) }

    private fun charger(km: Int, ccs: Double? = null, chademo: Double? = null, price: Double? = null) = GasStation(
        id = "km$km",
        name = "Şarj $km",
        brand = FuelBrand.DIGER,
        latitude = at(km.toDouble()).latitude,
        longitude = 30.0,
        ccsKw = ccs,
        chademoKw = chademo,
        kwhPriceTL = price
    )

    private val start = CityLocation("Başlangıç", "", 39.0, 30.0)

    private fun plan(
        car: VehicleProfile,
        totalKm: Int,
        stations: List<GasStation>,
        forced: Map<Int, String> = emptyMap(),
        routeMinutes: Double? = null
    ): TripPlanResult {
        val end = at(totalKm.toDouble())
        return EvChargePlanner(GasStationRepository(initialStations = stations)).calculateTripPlan(
            origin = start,
            destination = CityLocation("Varış", "", end.latitude, end.longitude),
            routePoints = route(totalKm),
            vehicleProfile = car,
            forcedStationIds = forced,
            routeDurationMinutes = routeMinutes
        )
    }

    /** 75 kWh, 19 kWh/100 km, 150 kW, CCS2; şarj durağına en az %15, varışta en az %20. */
    private fun carA(startPercent: Double) = VehicleProfile(
        fuelType = FuelType.ELEKTRIK,
        tankCapacityLiters = 75.0,
        consumptionPer100Km = 19.0,
        currentLevelPercent = startPercent,
        reserveThresholdPercent = 15.0,
        maxChargeKw = 150.0,
        evArrivalPercent = 20.0
    )

    private val ex2Stations = listOf(
        charger(120, ccs = 180.0),
        charger(230, ccs = 50.0),
        charger(260, ccs = 120.0),
        charger(380, ccs = 60.0)
    )

    @Test
    fun `duz bolgede sure enerji bolu guc`() {
        assertEquals(18.0, EvChargePlanner.chargeMinutes(75.0, 10.0, 50.0, 100.0), 0.01)
    }

    @Test
    fun `50-80 arasi analitik integrale yakin`() {
        // İntegral: 0,75 kWh/% × 60 / 100 kW × 60 × ln 2
        assertEquals(18.714, EvChargePlanner.chargeMinutes(75.0, 50.0, 80.0, 100.0), 0.01)
    }

    @Test
    fun `istasyon gucu sinirliyken arac egrisi yalnizca ustte devreye girer`() {
        // 150 kW'lık araç 50 kW'lık istasyonda %20-80 arası hep 50 kW çeker: 45 kWh / 50 kW = 54 dk.
        assertEquals(54.0, EvChargePlanner.chargeMinutes(75.0, 20.0, 80.0, stationKw = 50.0, carKw = 150.0), 0.01)
    }

    @Test
    fun `sarji az suruculer hemen yanindaki istasyonu kullanir`() {
        val result = plan(
            carA(20.0), 300,
            listOf(charger(3, ccs = 150.0), charger(60, ccs = 150.0), charger(200, ccs = 150.0))
        )

        assertEquals("km3", result.stops.first().station.id)
        assertNull(result.warning)
        assertTrue(result.stops.all { it.arrivalFuelLevelPercent >= 15.0 - 1e-6 })
    }

    @Test
    fun `varis hedefi rezervin altinda secilebilir`() {
        // %18 ile 20 km: varışta ~%12,9 kalır; kullanıcı %5 istedi, rezerv (%15) şarj durağı içindir.
        val result = plan(carA(18.0).copy(evArrivalPercent = 5.0), 20, listOf(charger(10, ccs = 150.0)))

        assertEquals(0, result.stopsCount)
        assertNull(result.warning)
        assertEquals(12.93, result.arrivalFuelPercent, 0.1)
    }

    @Test
    fun `kucuk bataryayla cok uzun rota on iki durakta kesilmez`() {
        val frontera = VehicleProfile(
            fuelType = FuelType.ELEKTRIK,
            tankCapacityLiters = 44.0,
            consumptionPer100Km = 22.2,
            currentLevelPercent = 100.0,
            maxChargeKw = 100.0
        )
        val chargers = (40 until 1900 step 40).map { charger(it, ccs = 100.0) }
        val result = plan(frontera, 1900, chargers)

        assertTrue("durak sayısı ${result.stopsCount}", result.stopsCount > 12)
        assertNull(result.warning)
        assertTrue(result.arrivalFuelPercent >= 20.0 - 1e-6)
    }

    @Test
    fun `son durakta yuzde doksan varisa yetiyorsa kisa ikinci durak acilmaz`() {
        // 88,5 kWh, 17 kWh/100 km: km 312'den varışa %20 ile ulaşmak için ~%88 gerekir.
        // %80'de durup 2 dakikalık ikinci bir şarj yerine ilk durakta %88'e kadar şarj edilir.
        val togg = VehicleProfile(
            fuelType = FuelType.ELEKTRIK,
            tankCapacityLiters = 88.5,
            consumptionPer100Km = 17.0,
            currentLevelPercent = 80.0,
            maxChargeKw = 180.0
        )
        val result = plan(togg, 667, listOf(charger(312, ccs = 250.0), charger(649, ccs = 180.0)))

        assertEquals(listOf("km312"), result.stops.map { it.station.id })
        // Rota 5 km'lik adımlarla 665. km'de biter: (353 km × 0,17 + %20) / 88,5 kWh = %87,8 -> %88.
        assertEquals(88.0, result.stops.single().departurePercent!!, 0.01)
        assertTrue(result.arrivalFuelPercent >= 20.0 - 1e-6)
        assertNull(result.warning)
    }

    @Test
    fun `sarj rezervin altindayken yakindaki istasyona gidilir`() {
        val result = plan(carA(10.0), 300, listOf(charger(2, ccs = 150.0), charger(150, ccs = 150.0)))

        assertEquals("km2", result.stops.first().station.id)
        assertTrue(result.stops.first().arrivalFuelLevelPercent < 15.0)
        assertTrue(result.arrivalFuelPercent >= 20.0 - 1e-6)
        assertNull(result.warning)
    }

    @Test
    fun `sarj rezervin altindayken menzilde istasyon yoksa en yakina git der`() {
        val result = plan(carA(8.0), 300, listOf(charger(150, ccs = 150.0)))

        assertEquals(0, result.stopsCount)
        assertTrue(result.warning.orEmpty(), result.warning.orEmpty().contains("Bataryan çok az"))
    }

    @Test
    fun `istasyona yuksek sarjla varilan bos durak planlanmaz`() {
        // %100 ile çıkıp km 40'a ~%90 ile varılır; orada durmak 0 kWh eklerdi. km 400'e ulaşmak için
        // tek mantıklı durak km 70'te %100'e kadar şarj.
        val result = plan(
            carA(100.0), 600,
            listOf(charger(40, ccs = 150.0), charger(70, ccs = 50.0), charger(400, ccs = 150.0))
        )

        assertTrue(result.stops.all { it.refuelLiters > 0.5 })
        assertEquals(listOf("km70", "km400"), result.stops.map { it.station.id })
        assertNull(result.warning)
    }

    @Test
    fun `art arda yakin iki durak yerine onceki uzatilir`() {
        val car = VehicleProfile(
            fuelType = FuelType.ELEKTRIK,
            tankCapacityLiters = 60.0,
            consumptionPer100Km = 18.0,
            currentLevelPercent = 80.0,
            maxChargeKw = 100.0
        )
        val result = plan(car, 600, listOf(charger(150, ccs = 240.0), charger(156, ccs = 50.0), charger(420, ccs = 180.0)))

        assertEquals(listOf("km150", "km420"), result.stops.map { it.station.id })
        assertEquals(100.0, result.stops.first().departurePercent!!, 0.01)
        assertNull(result.warning)
    }

    @Test
    fun `menzil yetiyorsa durak yok`() {
        val result = plan(carA(90.0), 250, emptyList())

        assertEquals(0, result.stopsCount)
        assertNull(result.warning)
        assertEquals(26.67, result.arrivalFuelPercent, 0.1)
    }

    @Test
    fun `hizli sarj yavas olana tercih edilir ve son durak yalnizca gerekeni alir`() {
        val result = plan(carA(85.0), 450, ex2Stations, routeMinutes = 290.0)

        assertEquals(1, result.stopsCount)
        val stop = result.stops.single()
        assertEquals("km260", stop.station.id)
        assertEquals(120.0, stop.chargeKw!!, 0.01)
        assertEquals(19.13, stop.arrivalFuelLevelPercent, 0.1)
        assertEquals(69.0, stop.departurePercent!!, 0.01)
        assertEquals(37.40, stop.refuelLiters, 0.1)
        assertEquals(18.91, stop.chargeMinutes!!, 0.2)
        assertEquals(560.6, stop.estimatedRefuelCostTL, 1.0)
        assertEquals(20.87, result.arrivalFuelPercent, 0.1)
        assertEquals(313, result.estimatedDrivingTimeMinutes)
        assertNull(result.warning)
    }

    @Test
    fun `erken hizli sarj iki kisa duraga yol acar`() {
        val result = plan(carA(80.0), 450, ex2Stations)

        assertEquals(listOf("km120", "km260"), result.stops.map { it.station.id })
        assertEquals(listOf(80.0, 69.0), result.stops.map { it.departurePercent })
        assertEquals(20.87, result.arrivalFuelPercent, 0.1)
    }

    @Test
    fun `ara durak 80'de yetmiyorsa 100'e kadar sarj eder`() {
        val car = VehicleProfile(
            fuelType = FuelType.ELEKTRIK,
            tankCapacityLiters = 60.0,
            consumptionPer100Km = 18.0,
            currentLevelPercent = 100.0,
            maxChargeKw = 100.0
        )
        val result = plan(car, 700, listOf(charger(200, ccs = 100.0), charger(480, ccs = 100.0)), routeMinutes = 480.0)

        assertEquals(listOf(100.0, 86.0), result.stops.map { it.departurePercent })
        assertEquals(47.51, result.stops[0].chargeMinutes!!, 0.2)
        assertEquals(32.15, result.stops[1].chargeMinutes!!, 0.2)
        assertEquals(20.0, result.arrivalFuelPercent, 0.1)
        assertEquals(569, result.estimatedDrivingTimeMinutes)
        assertNull(result.warning)
    }

    private val leaf = VehicleProfile(
        fuelType = FuelType.ELEKTRIK,
        tankCapacityLiters = 36.0,
        consumptionPer100Km = 17.0,
        currentLevelPercent = 100.0,
        maxChargeKw = 50.0,
        connector = DcConnector.CHADEMO,
        evArrivalPercent = 15.0
    )

    @Test
    fun `uyumsuz soket hic secilmez`() {
        val result = plan(leaf, 250, listOf(charger(110, ccs = 120.0), charger(150, ccs = 50.0, chademo = 50.0)))

        val stop = result.stops.single()
        assertEquals("km150", stop.station.id)
        assertEquals(50.0, stop.chargeKw!!, 0.01)
        assertEquals(63.0, stop.departurePercent!!, 0.01)
        assertEquals(15.33, stop.chargeMinutes!!, 0.2)
    }

    @Test
    fun `uygun soket yoksa uyari`() {
        val result = plan(leaf, 250, listOf(charger(110, ccs = 120.0)))

        assertEquals(0, result.stopsCount)
        assertTrue(result.warning.orEmpty(), result.warning.orEmpty().contains("bulunamadı"))
    }

    @Test
    fun `menzilde sarj yoksa baslangic yuzdesi onerilir`() {
        val result = plan(carA(30.0), 450, listOf(charger(180, ccs = 120.0)))

        assertEquals(0, result.stopsCount)
        assertTrue(result.warning.orEmpty(), result.warning.orEmpty().contains("%61"))
    }

    @Test
    fun `operator fiyati kullanilir`() {
        val result = plan(carA(85.0), 450, listOf(charger(260, ccs = 120.0, price = 16.49)))

        assertEquals(649.2, result.stops.single().estimatedRefuelCostTL, 1.0)
        assertEquals(16.49, result.stops.single().pricePerLiterTL, 0.001)
    }

    @Test
    fun `her durakta rezervin ustunde varilir ve varis hedefi tutar`() {
        val dense = (50 until 900 step 50).map { charger(it, ccs = 150.0) }
        val result = plan(carA(80.0), 900, dense)

        assertEquals(3, result.stopsCount)
        result.stops.forEach { stop ->
            assertTrue("varış %${stop.arrivalFuelLevelPercent}", stop.arrivalFuelLevelPercent >= 15.0 - 1e-6)
            val departure = stop.departurePercent!!
            assertTrue(stop.arrivalFuelLevelPercent <= departure && departure <= 100.0)
            assertEquals(75.0 * (departure - stop.arrivalFuelLevelPercent) / 100, stop.refuelLiters, 1e-6)
            assertEquals(stop.refuelLiters / 0.95 * stop.pricePerLiterTL, stop.estimatedRefuelCostTL, 1e-6)
        }
        assertTrue(result.arrivalFuelPercent >= 20.0 - 1e-6)
    }

    @Test
    fun `secilen alternatif durak olarak kullanilir`() {
        val result = plan(carA(85.0), 450, ex2Stations, forced = mapOf(1 to "km230"))

        assertEquals("km230", result.stops.first().station.id)
        assertTrue(result.stops.all { it.arrivalFuelLevelPercent >= 15.0 - 1e-6 })
        assertTrue(result.arrivalFuelPercent >= 20.0 - 1e-6)
    }

    @Test
    fun `yol servisi sapmasi buyuk istasyonu eler`() {
        val stations = listOf(charger(250, ccs = 150.0), charger(260, ccs = 150.0))
        val roads = DetourResolver { _, _, points ->
            points.map { point ->
                val id = stations.first { it.location == point }.id
                if (id == "km260") RoadDetour(extraKm = 25.0, extraMinutes = 22.0) else RoadDetour(0.2, 0.5)
            }
        }
        val result = EvChargePlanner(GasStationRepository(initialStations = stations)).calculateTripPlan(
            origin = start,
            destination = CityLocation("Varış", "", at(450.0).latitude, 30.0),
            routePoints = route(450),
            vehicleProfile = carA(85.0),
            detourResolver = roads
        )

        assertEquals("km250", result.stops.single().station.id)
        assertEquals(0.5, result.stops.single().detourMinutes!!, 1e-9)
    }
}
