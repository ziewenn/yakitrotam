package com.yakitrotam.app

import com.yakitrotam.app.data.model.*
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.domain.FuelOptimizerEngine
import com.yakitrotam.app.util.GeoUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Gerçek veriyle uçtan uca doğrulama.
 *
 * `src/test/resources/demo/` altındaki dosyalar canlı servislerden alınmıştır:
 *  - route.csv    : OSRM sürüş rotası (Kadıköy → Kızılay, 5186 nokta)
 *  - stations.csv : OpenStreetMap Overpass, rota koridorunda 391 gerçek istasyon
 *
 * Test hem planı doğrular hem de elle kontrol edilebilsin diye koordinatları basar.
 */
class DemoRoutePlanTest {

    private val istanbul = CityLocation("İstanbul (Kadıköy)", "İstanbul", 40.9912, 29.0276)
    private val ankara = CityLocation("Ankara (Kızılay)", "Ankara", 39.9208, 32.8541)

    /** 16.09.2026 tarihli Opet İstanbul Anadolu fiyatları. */
    private val price = FuelPriceSnapshot(
        pricesPerLiterTL = mapOf(
            FuelType.BENZIN to 80.16,
            FuelType.DIZEL to 95.53,
            FuelType.LPG to 38.48
        ),
        sourceName = "Opet güncel pompa fiyatları",
        sourceUrl = "https://www.opet.com.tr/akaryakit-fiyatlari",
        regionName = "İSTANBUL ANADOLU",
        priceDate = "16.09.2026",
        fetchedAtEpochMillis = 1L,
        estimatedTypes = setOf(FuelType.LPG)
    )

    private fun resource(name: String): List<String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("demo/$name")) {
            "demo/$name bulunamadı"
        }.bufferedReader().readLines().filter { it.isNotBlank() }

    private fun loadRoute(): List<LatLng> = resource("route.csv").map { line ->
        val (lat, lng) = line.split(",")
        LatLng(lat.toDouble(), lng.toDouble())
    }

    private fun loadStations(): List<GasStation> = resource("stations.csv")
        .drop(1)
        .mapNotNull { line ->
            val c = line.split("\t")
            if (c.size < 12) return@mapNotNull null
            fun yesNo(v: String): Boolean? = when (v.lowercase()) {
                "" -> null
                "no", "false" -> false
                else -> true
            }
            val brandTag = c[4]
            val nameTag = c[5]
            GasStation(
                id = "osm-${c[0]}-${c[1]}",
                name = nameTag.ifBlank { brandTag.ifBlank { "İsimsiz akaryakıt istasyonu" } },
                brand = FuelBrand.fromString(brandTag.ifBlank { nameTag }),
                latitude = c[2].toDouble(),
                longitude = c[3].toDouble(),
                highway = c[7],
                city = c[6],
                hasLpg = yesNo(c[9]),
                hasDiesel = yesNo(c[8]),
                hasGasoline = yesNo(c[10]),
                osmType = c[0],
                osmId = c[1].toLong(),
                openingHours = c[11].ifBlank { null }
            )
        }

    @Test
    fun `gercek istanbul ankara rotasi icin iki duraklı plan`() {
        val route = loadRoute()
        val stations = loadStations()
        val engine = FuelOptimizerEngine(GasStationRepository(initialStations = stations))

        // 40 L depolu, 9.5 L/100km tüketen, %30 dolu bir araç:
        // ilk etapta ~63 km menzil, dolu depoyla ~358 km → 435 km'lik rota için 2 durak gerekir.
        val car = VehicleProfile(
            fuelType = FuelType.BENZIN,
            consumptionPer100Km = 9.5,
            tankCapacityLiters = 40.0,
            currentLevelPercent = 30.0,
            reserveThresholdPercent = 15.0
        )

        val plan = engine.calculateTripPlan(
            origin = istanbul,
            destination = ankara,
            routePoints = route,
            vehicleProfile = car,
            preferredBrands = emptySet(),
            fuelPrice = price
        )

        printPlan(plan, stations.size)

        assertNull(plan.warning)
        assertEquals("Bu profil için tam 2 durak beklenir", 2, plan.stopsCount)

        val cumulative = GeoUtils.cumulativeDistancesKm(route)
        plan.stops.forEach { stop ->
            // Durak gerçekten OSM'de kayıtlı bir istasyon olmalı.
            assertTrue(stations.any { it.id == stop.station.id })
            // Ve gerçekten rotanın üstünde olmalı, rastgele bir koordinat değil.
            val projection = GeoUtils.projectOntoRoute(stop.station.location, route, cumulative)
            assertTrue(
                "${stop.station.name} rotadan ${projection.detourKm} km sapıyor",
                projection.detourKm <= 6.0
            )
        }
        assertTrue(plan.arrivalFuelLiters >= 0.0)
    }

    private val twoStopCar = VehicleProfile(
        fuelType = FuelType.BENZIN,
        consumptionPer100Km = 9.5,
        tankCapacityLiters = 40.0,
        currentLevelPercent = 30.0,
        reserveThresholdPercent = 15.0
    )

    @Test
    fun `her durak icin ulasilabilir alternatifler sunulur ve secilen alternatif uygulanir`() {
        val route = loadRoute()
        val engine = FuelOptimizerEngine(GasStationRepository(initialStations = loadStations()))
        val plan = engine.calculateTripPlan(istanbul, ankara, route, twoStopCar, fuelPrice = price)

        val first = plan.stops.first()
        assertTrue("İlk durak için alternatif olmalı", first.alternatives.isNotEmpty())
        assertTrue(first.alternatives.size <= 3)
        first.alternatives.forEach { alt ->
            assertNotEquals(first.station.id, alt.station.id)
            // Başlangıç yakıtıyla (rezerv hariç) bu alternatife gerçekten ulaşılabilmeli.
            val usable = twoStopCar.currentFuelLiters - twoStopCar.reserveLiters
            val needed = (alt.distanceFromOriginKm + alt.detourDistanceKm) / 100.0 * twoStopCar.consumptionPer100Km
            assertTrue("${alt.station.name} ulaşılamaz: $needed L > $usable L", needed <= usable + 1e-6)
        }

        val chosenAlternative = first.alternatives.last()
        val replanned = engine.calculateTripPlan(
            istanbul, ankara, route, twoStopCar,
            fuelPrice = price,
            forcedStationIds = mapOf(1 to chosenAlternative.station.id)
        )
        assertEquals(chosenAlternative.station.id, replanned.stops.first().station.id)
        assertNull("Yeni plan da varışa ulaşmalı", replanned.warning)
        assertTrue(replanned.arrivalFuelLiters >= 0.0)
    }

    @Test
    fun `paylasim metni duraklari tutari ve kodlanmis rota baglantisini icerir`() {
        val engine = FuelOptimizerEngine(GasStationRepository(initialStations = loadStations()))
        val plan = engine.calculateTripPlan(istanbul, ankara, loadRoute(), twoStopCar, fuelPrice = price)

        val text = com.yakitrotam.app.util.TripShareText.build(plan)
        println(text)

        assertTrue(text.startsWith("YakıtRotam yakıt planı"))
        plan.stops.forEach { stop -> assertTrue(text.contains(stop.station.name)) }
        assertTrue(text.contains("Pompada toplam"))
        assertTrue(text.contains("https://www.google.com/maps/dir/?api=1"))
        // Sohbet uygulamaları bağlantıyı "|" karakterinde kesmesin.
        assertTrue(text.contains("waypoints=") && text.contains("%7C"))
        assertFalse(text.contains("|"))
    }

    private fun printPlan(plan: TripPlanResult, stationCount: Int) {
        println()
        println("=".repeat(78))
        println("DEMO ROTA: ${plan.origin.name} → ${plan.destination.name}")
        println("Koridorda bulunan gerçek OSM istasyonu: $stationCount")
        println(
            "Mesafe: %.1f km (sapmalarla %.1f km) · Süre: %d sa %d dk".format(
                plan.totalDistanceKm,
                plan.totalDrivenDistanceKm,
                plan.estimatedDrivingTimeMinutes / 60,
                plan.estimatedDrivingTimeMinutes % 60
            )
        )
        println(
            "Araç: %.0f L depo · %.1f L/100km · başlangıç %%%.0f (%.1f L)".format(
                plan.vehicleProfile.tankCapacityLiters,
                plan.vehicleProfile.consumptionPer100Km,
                plan.vehicleProfile.currentLevelPercent,
                plan.vehicleProfile.currentFuelLiters
            )
        )
        println(
            "Fiyat: %.2f TL/L (%s, %s)".format(
                plan.fuelPrice.priceFor(plan.vehicleProfile.fuelType),
                plan.fuelPrice.sourceName,
                plan.fuelPrice.regionName
            )
        )
        println("-".repeat(78))
        plan.stops.forEach { stop ->
            println("DURAK ${stop.stopIndex}: ${stop.station.name}  [${stop.station.brand.displayName}]")
            println("  Koordinat : ${"%.6f".format(stop.station.latitude)}, ${"%.6f".format(stop.station.longitude)}")
            println("  Haritada  : https://www.google.com/maps/search/?api=1&query=${stop.station.latitude},${stop.station.longitude}")
            println("  OSM       : https://www.openstreetmap.org/${stop.station.osmType}/${stop.station.osmId}")
            println("  Konum     : ${listOf(stop.station.city, stop.station.highway).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "-" }}")
            println("  Rota km   : %.1f. km (bu etap %.1f km, sapma %.2f km)".format(stop.distanceFromOriginKm, stop.legDistanceKm, stop.detourDistanceKm))
            println("  Varışta   : %.1f L (%%%.0f)".format(stop.arrivalFuelLiters, stop.arrivalFuelLevelPercent))
            println("  Alınacak  : %.1f L → %.0f TL".format(stop.refuelLiters, stop.estimatedRefuelCostTL))
            println()
        }
        println(
            "VARIŞ: %.1f L (%%%.0f) kalır · Pompada toplam %.0f TL · Yakılan %.1f L".format(
                plan.arrivalFuelLiters,
                plan.arrivalFuelPercent,
                plan.totalRefuelCostTL,
                plan.totalFuelConsumedLiters
            )
        )
        println("=".repeat(78))
    }
}
