package com.yakitrotam.app.data.repository

import android.util.Log
import com.yakitrotam.app.data.model.FuelPriceSnapshot
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.util.Province
import com.yakitrotam.app.util.Provinces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Bir ilin pompa fiyatları (TL/L). [lpg] null ise otogaz kaynağına ulaşılamamıştır. */
data class ProvincePrices(val benzin: Double, val dizel: Double, val lpg: Double?) {
    /** Tahmine düşmeden, yalnızca kaynaktan gelen fiyat. */
    fun livePrice(fuelType: FuelType): Double? = when (fuelType) {
        FuelType.BENZIN -> benzin
        FuelType.DIZEL -> dizel
        FuelType.LPG -> lpg
    }
}

/** Tüm illerin fiyatları; her durak kendi ilinin fiyatıyla hesaplanabilsin diye tek seferde çekilir. */
class PriceTable(
    private val byProvinceKey: Map<String, ProvincePrices>,
    val fetchedAtEpochMillis: Long
) {
    fun pricesFor(province: Province): ProvincePrices? = byProvinceKey[province.key]

    /** [point]'in bulunduğu ildeki litre fiyatı; otogaz kaynağı yoksa benzine oranlı tahmin. */
    fun pricePerLiter(point: LatLng, fuelType: FuelType): Double? =
        pricesFor(Provinces.nearest(point))?.let { it.livePrice(fuelType) ?: estimateLpg(it.benzin) }

    fun snapshotFor(province: Province): FuelPriceSnapshot? {
        val prices = pricesFor(province) ?: return null
        return FuelPriceSnapshot(
            pricesPerLiterTL = mapOf(
                FuelType.BENZIN to prices.benzin,
                FuelType.DIZEL to prices.dizel,
                FuelType.LPG to (prices.lpg ?: estimateLpg(prices.benzin))
            ),
            sourceName = if (prices.lpg != null) {
                "Opet ve Petrol Ofisi güncel pompa fiyatları"
            } else {
                "Opet güncel pompa fiyatları"
            },
            sourceUrl = "https://www.opet.com.tr/akaryakit-fiyatlari",
            regionName = province.name,
            priceDate = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("tr")).format(Date(fetchedAtEpochMillis)),
            fetchedAtEpochMillis = fetchedAtEpochMillis,
            estimatedTypes = if (prices.lpg == null) setOf(FuelType.LPG) else emptySet()
        )
    }

    private fun estimateLpg(benzin: Double): Double = Math.round(benzin * LPG_TO_GASOLINE_RATIO * 100.0) / 100.0

    private companion object {
        /** Otogaz kaynağına ulaşılamazsa: otogaz uzun süredir benzinin ~%45'i seviyesinde. */
        const val LPG_TO_GASOLINE_RATIO = 0.45
    }
}

/**
 * Güncel pompa fiyatlarını herkese açık, anahtarsız iki kaynaktan çeker:
 * benzin ve motorin Opet'in fiyat servisinden, otogaz (Opet yayınlamadığı için)
 * Petrol Ofisi'nin fiyat sayfasından. İkisi de tüm illeri tek istekte verir.
 */
class FuelPriceRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    @Volatile
    private var cachedTable: PriceTable? = null

    /** Tüm illerin fiyat tablosu; [CACHE_TTL_MILLIS] boyunca yeniden kullanılır. Ulaşılamazsa null. */
    suspend fun getTable(): PriceTable? = withContext(Dispatchers.IO) {
        val cached = cachedTable
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtEpochMillis < CACHE_TTL_MILLIS) {
            return@withContext cached
        }
        val (opet, petrolOfisi) = coroutineScope {
            val opetJob = async { fetch(OPET_URL)?.let { runCatching { PriceParsers.parseOpet(it) }.getOrNull() } }
            val poJob = async { fetch(PETROL_OFISI_URL)?.let { runCatching { PriceParsers.parsePetrolOfisi(it) }.getOrNull() } }
            opetJob.await().orEmpty() to poJob.await().orEmpty()
        }
        val merged = PriceParsers.merge(opet, petrolOfisi)
        if (merged.isEmpty()) return@withContext cached
        PriceTable(merged, System.currentTimeMillis()).also { cachedTable = it }
    }

    /** [point]'in ilindeki güncel fiyatlar; konum yoksa ya da il bulunamazsa İstanbul baz alınır. */
    suspend fun getPrices(point: LatLng?): FuelPriceSnapshot {
        val table = getTable() ?: return FuelPriceSnapshot.fallback()
        val province = point?.let(Provinces::nearest) ?: DEFAULT_PROVINCE
        return table.snapshotFor(province) ?: table.snapshotFor(DEFAULT_PROVINCE) ?: FuelPriceSnapshot.fallback()
    }

    private fun fetch(url: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", "YakitRotam/1.0 (Android)").build()
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string()?.takeIf { it.isNotBlank() }
            else null.also { Log.w("FuelPriceRepository", "HTTP ${response.code} $url") }
        }
    } catch (e: Exception) {
        Log.w("FuelPriceRepository", "Fiyatlar alınamadı: $url", e)
        null
    }

    private companion object {
        const val OPET_URL = "https://api.opet.com.tr/api/fuelprices/allprices"
        const val PETROL_OFISI_URL = "https://www.petrolofisi.com.tr/akaryakit-fiyatlari"
        const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L
        val DEFAULT_PROVINCE: Province = Provinces.all.first { it.name == "İstanbul Anadolu" }
    }
}

/** Kaynak yanıtlarını il anahtarı -> fiyat eşlemesine çevirir. Ağdan bağımsız, test edilebilir. */
internal object PriceParsers {
    private val OPET_GASOLINE = setOf("A100")          // Kurşunsuz Benzin 95
    private val OPET_DIESEL = setOf("A121", "A128")    // Motorin UltraForce / EcoForce

    fun parseOpet(body: String): Map<String, ProvincePrices> {
        val result = LinkedHashMap<String, ProvincePrices>()
        for (entry in Json.parseToJsonElement(body).jsonArray) {
            val district = entry.jsonObject
            val province = district["provinceName"]?.jsonPrimitive?.contentOrNull?.let(Provinces::byName) ?: continue
            var benzin: Double? = null
            var dizel: Double? = null
            for (price in district["prices"]?.jsonArray.orEmpty()) {
                val product = price.jsonObject
                val amount = product["amount"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0.0 } ?: continue
                when (product["productCode"]?.jsonPrimitive?.contentOrNull) {
                    in OPET_GASOLINE -> benzin = benzin ?: amount
                    in OPET_DIESEL -> dizel = dizel ?: amount
                }
            }
            if (benzin != null && dizel != null) result.putIfAbsent(province.key, ProvincePrices(benzin, dizel, null))
        }
        return result
    }

    private val ROW = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
    private val CELL = Regex("<t[dh][^>]*>(.*?)</t[dh]>", RegexOption.DOT_MATCHES_ALL)
    private val WITH_TAX = Regex("""class="with-tax"[^>]*>\s*([0-9]+(?:[.,][0-9]+)?)""")
    private val TAG = Regex("<[^>]+>")

    /**
     * Petrol Ofisi fiyat sayfasındaki tabloyu okur. Sütunlar başlıktan bulunur; sayfa düzeni
     * değişirse yanlış sütunu (ör. TL/KG fuel oil) otogaz sanmamak için değer benzine oranla doğrulanır.
     */
    fun parsePetrolOfisi(html: String): Map<String, ProvincePrices> {
        val rows = ROW.findAll(html).map { row -> CELL.findAll(row.groupValues[1]).map { it.groupValues[1] }.toList() }.toList()
        val header = rows.firstOrNull { cells -> cells.any { it.contains("otogaz", ignoreCase = true) } }
            ?.map { TAG.replace(it, " ").lowercase(Locale.ROOT) }
            ?: return emptyMap()
        val gasolineColumn = header.indexOfFirst { "95" in it || "benzin" in it }
        val dieselColumn = header.indexOfFirst { "diesel" in it || "motorin" in it }
        val lpgColumn = header.indexOfFirst { "otogaz" in it }
        if (gasolineColumn < 0 || dieselColumn < 0) return emptyMap()

        val result = LinkedHashMap<String, ProvincePrices>()
        for (cells in rows) {
            fun price(column: Int): Double? = cells.getOrNull(column)
                ?.let { WITH_TAX.find(it)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() }
                ?.takeIf { it > 0.0 }

            val province = cells.firstOrNull()?.let { Provinces.byName(TAG.replace(it, " ")) } ?: continue
            val benzin = price(gasolineColumn) ?: continue
            val dizel = price(dieselColumn) ?: continue
            val lpg = price(lpgColumn)?.takeIf { it in benzin * 0.25..benzin * 0.8 }
            result.putIfAbsent(province.key, ProvincePrices(benzin, dizel, lpg))
        }
        return result
    }

    /** Benzin ve motorinde Opet esas alınır (yoksa Petrol Ofisi); otogaz yalnızca Petrol Ofisi'nden gelir. */
    fun merge(opet: Map<String, ProvincePrices>, petrolOfisi: Map<String, ProvincePrices>): Map<String, ProvincePrices> =
        (opet.keys + petrolOfisi.keys).associateWith { key ->
            val base = opet[key] ?: petrolOfisi.getValue(key)
            base.copy(lpg = petrolOfisi[key]?.lpg)
        }
}
