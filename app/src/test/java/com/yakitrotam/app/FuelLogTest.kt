package com.yakitrotam.app

import com.yakitrotam.app.data.model.FuelLog
import com.yakitrotam.app.data.model.LogEntry
import com.yakitrotam.app.data.model.SavedRoute
import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.operatorKey
import org.junit.Assert.*
import org.junit.Test

class FuelLogTest {
    private fun entry(km: Double, litres: Double, price: Double, full: Boolean = true, day: Long = 0) =
        LogEntry(id = km.toLong(), dateMillis = 1_790_000_000_000 + day * 86_400_000, odometerKm = km,
            amount = litres, totalPriceTL = price, fullFill = full)

    @Test
    fun `tam dolumdan tam doluma tuketim`() {
        // İlk tam dolum başlangıç; sonraki 30 + 12 L, 600 km'de yakıldı -> 7 L/100 km.
        val stats = FuelLog.stats(
            listOf(entry(10_000.0, 40.0, 3200.0), entry(10_300.0, 12.0, 960.0, full = false), entry(10_600.0, 30.0, 2400.0)),
            electric = false
        )
        assertEquals(7.0, stats.consumptionPer100Km!!, 1e-9)
        assertEquals(560.0, stats.costPer100KmTL!!, 1e-9)
        assertEquals(600.0, stats.measuredKm, 1e-9)
    }

    @Test
    fun `tek kayitta tuketim yok, elektrik kayitlari ayri sayilir`() {
        assertNull(FuelLog.stats(listOf(entry(1000.0, 40.0, 3000.0)), electric = false).consumptionPer100Km)
        val ev = entry(1000.0, 30.0, 400.0).copy(electric = true)
        assertNull(FuelLog.stats(listOf(ev, entry(1500.0, 30.0, 400.0)), electric = true).consumptionPer100Km)
    }

    @Test
    fun `sarj agi adi ve rota etiketi`() {
        assertEquals(operatorKey("Eşarj"), operatorKey("EŞARJ"))
        assertEquals(operatorKey("WAT Mobilite"), operatorKey("wat mobilite"))
        val route = SavedRoute(CityLocation("Konumum (Yeniköy, Başiskele)", "", 40.7, 29.9), CityLocation("Ankara (Çankaya)", "", 39.9, 32.8))
        assertEquals("Yeniköy → Ankara", route.label)
    }
}
