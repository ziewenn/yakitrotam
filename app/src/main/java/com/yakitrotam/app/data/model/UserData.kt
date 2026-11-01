package com.yakitrotam.app.data.model

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.Locale

/** Tek dokunuşla planlanan kayıtlı rota ("Ev → Memleket"). */
@Serializable
data class SavedRoute(
    val origin: CityLocation,
    val destination: CityLocation
) {
    val label: String
        get() = "${shortPlace(origin.name)} → ${shortPlace(destination.name)}"
}

/** "İstanbul (Kadıköy / Anadolu)" -> "İstanbul"; "Konumum (Yeniköy, Başiskele)" -> "Yeniköy". */
fun shortPlace(name: String): String {
    val inner = name.substringAfter("Konumum (", "").substringBefore(")").substringBefore(",")
    return (inner.ifBlank { name.substringBefore(" (").substringBefore(",") }).trim()
}

/** Defter kaydı: bir dolum ya da şarj. [amount] litre ya da kWh. */
@Serializable
data class LogEntry(
    val id: Long,
    val dateMillis: Long,
    val odometerKm: Double,
    val amount: Double,
    val totalPriceTL: Double,
    /** Depo tamamen dolduruldu mu (ya da batarya hedef yüzdeye kadar şarj edildi mi). */
    val fullFill: Boolean = true,
    val electric: Boolean = false
) {
    val unitPriceTL: Double
        get() = if (amount > 0) totalPriceTL / amount else 0.0
}

/** Defterden çıkan gerçek tüketim ve harcama. */
data class LogStats(
    /** L ya da kWh / 100 km; iki tam dolum arası yoksa null. */
    val consumptionPer100Km: Double?,
    val costPer100KmTL: Double?,
    val thisMonthSpendTL: Double,
    val measuredKm: Double
)

object FuelLog {
    /**
     * Tam dolumdan tam dolum yöntemi: iki tam dolum arasında alınan tüm yakıt, aradaki km'de
     * yakılmıştır. İlk tam dolumdan önceki ve son tam dolumdan sonraki kayıtlar hesaba girmez.
     */
    fun stats(entries: List<LogEntry>, electric: Boolean, now: Long = System.currentTimeMillis()): LogStats {
        val sorted = entries.filter { it.electric == electric }.sortedBy { it.odometerKm }
        var amount = 0.0
        var price = 0.0
        var km = 0.0
        var lastFull: LogEntry? = null
        var pendingAmount = 0.0
        var pendingPrice = 0.0
        for (entry in sorted) {
            if (lastFull != null) {
                pendingAmount += entry.amount
                pendingPrice += entry.totalPriceTL
            }
            if (entry.fullFill) {
                val previous = lastFull
                if (previous != null && entry.odometerKm > previous.odometerKm) {
                    amount += pendingAmount
                    price += pendingPrice
                    km += entry.odometerKm - previous.odometerKm
                }
                lastFull = entry
                pendingAmount = 0.0
                pendingPrice = 0.0
            }
        }
        val calendar = Calendar.getInstance(Locale.forLanguageTag("tr")).apply { timeInMillis = now }
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)
        val thisMonth = sorted.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year
        }.sumOf { it.totalPriceTL }
        return LogStats(
            consumptionPer100Km = if (km > 0) amount / km * 100 else null,
            costPer100KmTL = if (km > 0) price / km * 100 else null,
            thisMonthSpendTL = thisMonth,
            measuredKm = km
        )
    }
}

/** Şarj ağı adlarını karşılaştırma anahtarı: büyük/küçük harf ve Türkçe i/ı farkı yok sayılır. */
fun operatorKey(name: String): String = name
    .replace('İ', 'i').replace('I', 'i').replace('ı', 'i')
    .lowercase(Locale.ROOT)
    .replace("̇", "")
    .filter { it.isLetterOrDigit() }

/** Tercih sayfasında gösterilen büyük şarj ağları (EPDK listesindeki DC istasyon sayısına göre). */
val KNOWN_OPERATORS = listOf(
    "Trugo", "ZES", "Eşarj", "WAT Mobilite", "En Yakıt", "Otopriz", "Astor", "Otojet", "5 Şarj",
    "OnCharge", "Ovolt", "Shell", "D-Charge", "K-Şarj", "Voltrun", "otoWATT", "Aksa Şarj",
    "Beefull", "Tunçmatik", "Powerşarj"
)
