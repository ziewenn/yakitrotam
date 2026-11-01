package com.yakitrotam.app

import com.yakitrotam.app.data.model.*
import com.yakitrotam.app.data.repository.Endpoints
import com.yakitrotam.app.data.repository.OverpassStationSource
import com.yakitrotam.app.data.repository.PriceParsers
import com.yakitrotam.app.data.repository.PriceTable
import com.yakitrotam.app.data.repository.SavedTripState
import com.yakitrotam.app.util.TripShareText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Elektrikli araç için veri katmanı: kayıt, istasyon dosyası, fiyat, ayar ve paylaşım. */
class EvDataTest {

    @Test
    fun `elektrikli profil ve diger arac kaydedilip aynen geri yuklenir`() {
        val ev = VehicleProfile.EV_DEFAULT.copy(
            name = "Togg T10X", maxChargeKw = 180.0, connector = DcConnector.CHADEMO, evArrivalPercent = 25.0
        )
        val fuel = VehicleProfile(consumptionPer100Km = 7.2)
        val restored = SavedTripState.decode(SavedTripState.encode(SavedTripState(vehicleProfile = ev, otherVehicle = fuel)))

        assertEquals(ev, restored.vehicleProfile)
        assertEquals(fuel, restored.otherVehicle)
    }

    @Test
    fun `yeni alanlari bilmeyen eski kayit varsayilanlarla okunur`() {
        val old = """{"vehicleProfile":{"fuelType":"DIZEL","consumptionPer100Km":6.1,"tankCapacityLiters":55.0}}"""
        val restored = SavedTripState.decode(old)

        assertEquals(FuelType.DIZEL, restored.vehicleProfile.fuelType)
        assertEquals(6.1, restored.vehicleProfile.consumptionPer100Km, 1e-9)
        assertEquals(DcConnector.CCS2, restored.vehicleProfile.connector)
        assertNull(restored.otherVehicle)
    }

    @Test
    fun `bilinmeyen yakit turu kaydi silmez varsayilana doner`() {
        // Daha yeni bir sürümün yazdığı ama bu sürümün tanımadığı tür (geri dönülürse).
        val future = """{"vehicleProfile":{"fuelType":"HIDROJEN","tankCapacityLiters":42.0},"selectedBrands":["SHELL"]}"""
        val restored = SavedTripState.decode(future)

        assertEquals(FuelType.BENZIN, restored.vehicleProfile.fuelType)
        assertEquals(42.0, restored.vehicleProfile.tankCapacityLiters, 1e-9)
        assertEquals(setOf(FuelBrand.SHELL), restored.selectedBrands)
    }

    @Test
    fun `sarj istasyonu dosyasi soket gucu fiyat ve kaynakla okunur`() {
        val body = """{"elements":[
            {"type":"epdk","id":1000,"lat":41.17774,"lon":28.748019,"tags":{"name":"Kerimaba Petrol","operator":"ZES",
             "addr:city":"Arnavutköy","charge:ccs_kw":"180","charge:info":"ZES · CCS 180 kW ×2","charge:price":"16.49"}},
            {"type":"epdk","id":1001,"lat":39.9,"lon":32.8,"tags":{"name":"Trugo Ankara","charge:chademo_kw":"50,5"}},
            {"type":"node","id":7,"lat":40.0,"lon":30.0,"tags":{"amenity":"fuel","name":"Shell","fuel:diesel":"yes"}}
        ]}"""
        val stations = OverpassStationSource().parseElements(body)

        val zes = stations.first { it.name == "Kerimaba Petrol" }
        assertEquals("osm-epdk-1000", zes.id)
        assertEquals("EPDK", zes.dataSource)
        assertEquals(180.0, zes.ccsKw!!, 1e-9)
        assertNull(zes.chademoKw)
        assertEquals(16.49, zes.kwhPriceTL!!, 1e-9)
        assertEquals("Arnavutköy", zes.city)
        assertEquals("ZES · CCS 180 kW ×2", zes.chargeInfo)
        assertTrue(zes.supportsFuel(FuelType.ELEKTRIK))
        assertEquals(180.0, zes.dcKwFor(DcConnector.CCS2)!!, 1e-9)
        assertNull(zes.dcKwFor(DcConnector.CHADEMO))

        val trugo = stations.first { it.name == "Trugo Ankara" }
        assertEquals(50.5, trugo.dcKwFor(DcConnector.CHADEMO)!!, 1e-9)
        assertNull(trugo.kwhPriceTL)

        val shell = stations.first { it.name == "Shell" }
        assertEquals("OpenStreetMap", shell.dataSource)
        assertFalse(shell.supportsFuel(FuelType.ELEKTRIK))
    }

    @Test
    fun `canli yedegi olmayan kaynak liste yoksa ulasilamadi der`() = runBlocking {
        val source = OverpassStationSource(fixedEndpoints = emptyList(), datasetUrl = { "https://127.0.0.1:1/chargers.json" })
        val stations = withTimeout(15_000) {
            source.fetchAlongRoute(listOf(LatLng(39.0, 30.0), LatLng(39.5, 30.0)))
        }
        // null: liste hiç alınamadı (ağ hatası); boş liste "bu yolda istasyon yok" demek olurdu.
        assertNull(stations)
    }

    @Test
    fun `elektrik icin il pompa fiyati kullanilmaz`() {
        val table = PriceTable(mapOf("istanbulanadolu" to com.yakitrotam.app.data.repository.ProvincePrices(80.0, 100.0, 34.0)), 0L)
        assertNull(table.pricePerLiter(LatLng(40.99, 29.03), FuelType.ELEKTRIK))
        assertEquals(80.0, table.pricePerLiter(LatLng(40.99, 29.03), FuelType.BENZIN)!!, 1e-9)
        assertTrue(PriceParsers.parseOpet("[]").isEmpty())
    }

    @Test
    fun `uzak ayar sarj listesi ve acik harita adresini degistirir`() {
        val chargers = Endpoints.chargers
        val light = Endpoints.mapStyleLight
        try {
            assertTrue(Endpoints.apply("""{"chargers":"https://example.org/c.json","mapStyleLight":"https://example.org/s"}"""))
            assertEquals("https://example.org/c.json", Endpoints.chargers)
            assertEquals("https://example.org/s", Endpoints.mapStyleLight)
        } finally {
            Endpoints.apply("""{"chargers":"$chargers","mapStyleLight":"$light"}""")
        }
    }

    private fun evTrip(stopCount: Int): TripPlanResult {
        val profile = VehicleProfile.EV_DEFAULT
        val stops = (1..stopCount).map { index ->
            FuelStop(
                stopIndex = index,
                station = GasStation("s$index", "Şarj $index", FuelBrand.DIGER, 39.0 + index * 0.5, 30.0, city = "Bolu", ccsKw = 120.0),
                distanceFromOriginKm = index * 100.0,
                legDistanceKm = 100.0,
                arrivalFuelLevelPercent = 19.0,
                arrivalFuelLiters = 11.4,
                refuelLiters = 30.0,
                estimatedRefuelCostTL = 450.0,
                detourDistanceKm = 0.0,
                pricePerLiterTL = 14.24,
                chargeMinutes = 21.0,
                chargeKw = 120.0,
                departurePercent = 69.0
            )
        }
        return TripPlanResult(
            origin = CityLocation("İstanbul", "", 41.0, 29.0),
            destination = CityLocation("Ankara", "", 39.9, 32.8),
            totalDistanceKm = 450.0,
            totalDrivenDistanceKm = 450.0,
            estimatedDrivingTimeMinutes = 315,
            stops = stops,
            totalFuelConsumedLiters = 81.0,
            totalEstimatedCostTL = 1214.0,
            totalRefuelCostTL = 450.0 * stopCount,
            arrivalFuelLiters = 12.5,
            routePoints = listOf(LatLng(41.0, 29.0), LatLng(39.9, 32.8)),
            vehicleProfile = profile,
            preferredBrands = emptySet(),
            fuelPrice = FuelPriceSnapshot.fallback()
        )
    }

    @Test
    fun `elektrikli paylasim metni kWh ve sarj yazar pompa yazmaz`() {
        val text = TripShareText.build(evTrip(1))

        assertTrue(text, text.startsWith("YakıtRotam şarj planı"))
        assertTrue(text.contains("1 şarj molası"))
        assertTrue(text.contains("Varışta %19, %69 seviyesine kadar şarj: +30,0 kWh, ~21 dk"))
        assertTrue(text.contains("Şarjda toplam"))
        assertFalse(text.contains("Pompada"))
    }

    @Test
    fun `google haritalar baglantisi en fazla dokuz ara durak alir`() {
        val trip = evTrip(12)

        assertEquals(9, trip.mapsWaypoints.size)
        assertEquals(8, trip.googleMapsUrl.substringAfter("waypoints=").substringBefore("&").count { it == '|' })
        val shared = TripShareText.build(trip).lines().first { it.startsWith("Tüm rota") }
        assertEquals(8, Regex("%7C").findAll(shared).count())
    }
}
