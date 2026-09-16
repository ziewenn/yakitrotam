package com.yakitrotam.app

import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.data.model.VehicleProfile
import com.yakitrotam.app.data.repository.OverpassStationSource
import com.yakitrotam.app.data.repository.SavedTripState
import org.junit.Assert.*
import org.junit.Test

class PersistenceAndQueryTest {

    @Test
    fun `kaydedilen arac profili ve tercihler aynen geri yuklenir`() {
        val state = SavedTripState(
            vehicleProfile = VehicleProfile(
                fuelType = FuelType.LPG,
                consumptionPer100Km = 9.3,
                tankCapacityLiters = 42.0,
                currentLevelPercent = 35.0
            ),
            selectedBrands = setOf(FuelBrand.SHELL, FuelBrand.OPET),
            origin = CityLocation("Gebze", "Kocaeli", 40.80, 29.43),
            destination = CityLocation("Altunhisar", "Niğde", 38.00, 34.35)
        )

        assertEquals(state, SavedTripState.decode(SavedTripState.encode(state)))
    }

    @Test
    fun `bozuk veya bos kayit varsayilanlara doner`() {
        assertEquals(SavedTripState(), SavedTripState.decode(null))
        assertEquals(SavedTripState(), SavedTripState.decode("{bozuk json"))
        // Eski sürümden kalma, bilinmeyen alan içeren kayıt da okunabilmeli.
        val decoded = SavedTripState.decode("""{"selectedBrands":["BP"],"eskiAlan":1}""")
        assertEquals(setOf(FuelBrand.BP), decoded.selectedBrands)
    }

    @Test
    fun `son aranan yerler tekrar etmez ve sinirlidir`() {
        val gebze = CityLocation("Gebze", "Kocaeli", 40.80, 29.43)
        var state = SavedTripState()
        repeat(12) { i -> state = state.withRecentPlace(CityLocation("Yer $i", "", 39.0 + i, 30.0)) }
        state = state.withRecentPlace(gebze).withRecentPlace(gebze.copy(latitude = 40.8001))

        assertEquals(SavedTripState.MAX_RECENT_PLACES, state.recentPlaces.size)
        assertEquals("Gebze", state.recentPlaces.first().name)
        assertEquals(1, state.recentPlaces.count { it.name == "Gebze" })
    }

    @Test
    fun `koridor sorgusu rotanin her noktasini kapsar`() {
        val route = javaClass.classLoader!!.getResourceAsStream("demo/route.csv")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }
            .map { line -> line.split(",").let { LatLng(it[0].toDouble(), it[1].toDouble()) } }

        val query = OverpassStationSource.buildCorridorQuery(route)
        val boxes = Regex("""\(([-\d.]+),([-\d.]+),([-\d.]+),([-\d.]+)\);""")
            .findAll(query)
            .map { m -> m.groupValues.drop(1).map(String::toDouble) }
            .toList()

        // 434 km / 25 km ≈ 18 parça; tek dev sorgu değil, küçük kutular olmalı.
        assertTrue("Kutu sayısı beklenmedik: ${boxes.size}", boxes.size in 15..25)
        route.forEach { p ->
            assertTrue(
                "$p hiçbir kutunun içinde değil",
                boxes.any { (s, w, n, e) -> p.latitude in s..n && p.longitude in w..e }
            )
        }
        assertTrue(query.startsWith("[out:json]"))
        assertTrue(query.trimEnd().endsWith("out center tags;"))
    }
}
