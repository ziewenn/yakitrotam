package com.yakitrotam.app.data.repository

import com.yakitrotam.app.data.model.FuelPriceSnapshot
import com.yakitrotam.app.data.model.FuelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Güncel pompa fiyatlarını Opet'in herkese açık, anahtarsız fiyat servisinden çeker.
 * (Google/CollectAPI gibi ücretli servislere ihtiyaç yoktur.)
 *
 * Servis benzin ve motorini il bazında verir; otogaz (LPG) yayınlamadığı için
 * LPG fiyatı benzine oranlanarak tahmin edilir ve arayüzde "tahmini" işaretlenir.
 */
class FuelPriceRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val turkish: Locale = Locale.forLanguageTag("tr")
    private var cachedSnapshot: FuelPriceSnapshot? = null
    private var cachedProvinceCode: Int? = null

    /**
     * [provinceName] için güncel fiyatları döndürür. İl eşleşmezse İstanbul baz alınır.
     * Aynı il için sonuç [CACHE_TTL_MILLIS] boyunca yeniden kullanılır.
     */
    suspend fun getPrices(provinceName: String?): FuelPriceSnapshot = withContext(Dispatchers.IO) {
        val provinceCode = resolveProvinceCode(provinceName)
        val cached = cachedSnapshot
        if (cached != null && cachedProvinceCode == provinceCode &&
            System.currentTimeMillis() - cached.fetchedAtEpochMillis < CACHE_TTL_MILLIS
        ) {
            return@withContext cached
        }

        val snapshot = runCatching { fetchSnapshot(provinceCode) }.getOrNull()
            ?: cached
            ?: FuelPriceSnapshot.fallback()

        if (!snapshot.isFallback) {
            cachedSnapshot = snapshot
            cachedProvinceCode = provinceCode
        }
        snapshot
    }

    private fun fetchSnapshot(provinceCode: Int): FuelPriceSnapshot? {
        val body = get(BASE_URL + "/prices?ProvinceCode=" + provinceCode) ?: return null
        val districts = JSONArray(body)
        if (districts.length() == 0) return null

        // İl içindeki ilçe fiyatları birkaç kuruş oynayabilir; ortalaması alınır.
        val sums = HashMap<FuelType, Double>()
        val counts = HashMap<FuelType, Int>()
        var regionName = ""

        for (i in 0 until districts.length()) {
            val district = districts.optJSONObject(i) ?: continue
            if (regionName.isBlank()) regionName = district.optString("provinceName")
            val prices = district.optJSONArray("prices") ?: continue
            val seenInDistrict = HashSet<FuelType>()
            for (j in 0 until prices.length()) {
                val price = prices.optJSONObject(j) ?: continue
                val fuelType = PRODUCT_CODES[price.optString("productCode")] ?: continue
                // Aynı ilçede birden fazla motorin ürünü var (Ultra/Eco); ilkini baz al.
                if (!seenInDistrict.add(fuelType)) continue
                val amount = price.optDouble("amount")
                if (amount.isNaN() || amount <= 0.0) continue
                sums[fuelType] = (sums[fuelType] ?: 0.0) + amount
                counts[fuelType] = (counts[fuelType] ?: 0) + 1
            }
        }

        val benzinCount = counts[FuelType.BENZIN] ?: return null
        val benzin = (sums[FuelType.BENZIN] ?: return null) / benzinCount
        val dizelCount = counts[FuelType.DIZEL] ?: 0
        val dizel = if (dizelCount > 0) {
            (sums[FuelType.DIZEL] ?: 0.0) / dizelCount
        } else {
            FuelType.DIZEL.fallbackPricePerLiterTL
        }

        return FuelPriceSnapshot(
            pricesPerLiterTL = mapOf(
                FuelType.BENZIN to benzin.round2(),
                FuelType.DIZEL to dizel.round2(),
                // ponytail: LPG için ücretsiz/anahtarsız yayın bulunamadı; benzine oranlı tahmin.
                // Gerçek bir otogaz beslemesi bulunursa burayı gerçek fiyatla değiştir.
                FuelType.LPG to (benzin * LPG_TO_GASOLINE_RATIO).round2()
            ),
            sourceName = "Opet güncel pompa fiyatları",
            sourceUrl = "https://www.opet.com.tr/akaryakit-fiyatlari",
            regionName = regionName.ifBlank { "Türkiye" },
            priceDate = SimpleDateFormat("dd.MM.yyyy", turkish).format(Date()),
            fetchedAtEpochMillis = System.currentTimeMillis(),
            estimatedTypes = setOf(FuelType.LPG)
        )
    }

    private fun resolveProvinceCode(provinceName: String?): Int {
        val needle = provinceName?.normalize().orEmpty()
        if (needle.isBlank()) return DEFAULT_PROVINCE_CODE

        val body = runCatching { get(BASE_URL + "/provinces") }.getOrNull() ?: return DEFAULT_PROVINCE_CODE
        val provinces = runCatching { JSONArray(body) }.getOrNull() ?: return DEFAULT_PROVINCE_CODE

        for (i in 0 until provinces.length()) {
            val province = provinces.optJSONObject(i) ?: continue
            val name = province.optString("name").normalize()
            if (name.isNotBlank() && (needle.contains(name) || name.contains(needle))) {
                return province.optInt("code", DEFAULT_PROVINCE_CODE)
            }
        }
        return DEFAULT_PROVINCE_CODE
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YakitRotam/1.0 (Android)")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()?.takeIf { it.isNotBlank() }
        }
    }

    private fun String.normalize(): String = trim()
        .lowercase(turkish)
        .filter { it.isLetter() || it.isWhitespace() }
        .trim()

    private fun Double.round2(): Double = Math.round(this * 100.0) / 100.0

    companion object {
        private const val BASE_URL = "https://api.opet.com.tr/api/fuelprices"
        private const val DEFAULT_PROVINCE_CODE = 34 // İstanbul
        private const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L

        /** Türkiye'de otogaz uzun süredir kurşunsuz benzinin ~%48'i seviyesinde seyrediyor. */
        private const val LPG_TO_GASOLINE_RATIO = 0.48

        private val PRODUCT_CODES = mapOf(
            "A100" to FuelType.BENZIN,  // Kurşunsuz Benzin 95
            "A121" to FuelType.DIZEL,   // Motorin UltraForce
            "A128" to FuelType.DIZEL    // Motorin EcoForce
        )
    }
}
