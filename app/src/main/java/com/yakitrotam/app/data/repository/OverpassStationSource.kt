package com.yakitrotam.app.data.repository

import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.GasStation
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.util.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Akaryakıt istasyonlarını OpenStreetMap / Overpass API'den canlı olarak çeker.
 * Ücretsiz ve anahtarsızdır; Google Places Nearby Search'ün yerini alır.
 *
 * Veriler ODbL lisanslıdır, kaynak gösterimi RouteSummaryScreen'de yapılır.
 */
class OverpassStationSource(
    private val endpoints: List<String> = DEFAULT_ENDPOINTS
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Rota koridorundaki ([corridorMeters] metre yarıçapında) tüm akaryakıt istasyonlarını getirir.
     *
     * Rota binlerce nokta içerebildiği için sorgu öncesi ~[samplingKm] km aralıkla seyreltilir;
     * aksi halde Overpass sorgu gövdesi yüzlerce KB olur ve sunucu reddeder.
     */
    suspend fun fetchAlongRoute(
        routePoints: List<LatLng>,
        corridorMeters: Int = 3000,
        samplingKm: Double = 8.0
    ): List<GasStation> = withContext(Dispatchers.IO) {
        if (routePoints.isEmpty()) return@withContext emptyList()

        val sampled = downsample(routePoints, samplingKm)
        val coordinateList = sampled.joinToString(",") {
            "%.5f,%.5f".format(java.util.Locale.US, it.latitude, it.longitude)
        }
        val query = """
            [out:json][timeout:60];
            nwr["amenity"="fuel"](around:$corridorMeters,$coordinateList);
            out center tags;
        """.trimIndent()

        for (endpoint in endpoints) {
            val stations = runCatching { requestStations(endpoint, query) }.getOrNull()
            if (!stations.isNullOrEmpty()) return@withContext stations
        }
        emptyList()
    }

    private fun requestStations(endpoint: String, query: String): List<GasStation> {
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", USER_AGENT)
            .post(FormBody.Builder().add("data", query).build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            return parseElements(body)
        }
    }

    private fun parseElements(body: String): List<GasStation> {
        val elements = JSONObject(body).optJSONArray("elements") ?: return emptyList()
        val stations = ArrayList<GasStation>(elements.length())

        for (i in 0 until elements.length()) {
            val element = elements.optJSONObject(i) ?: continue
            val center = element.optJSONObject("center")
            val lat = if (element.has("lat")) element.optDouble("lat") else center?.optDouble("lat")
            val lon = if (element.has("lon")) element.optDouble("lon") else center?.optDouble("lon")
            if (lat == null || lon == null || lat.isNaN() || lon.isNaN()) continue

            val tags = element.optJSONObject("tags") ?: JSONObject()
            val osmType = element.optString("type", "node")
            val osmId = element.optLong("id")

            val brandTag = tags.optString("brand").ifBlank { tags.optString("operator") }
            val nameTag = tags.optString("name")
            val brand = FuelBrand.fromString(brandTag.ifBlank { nameTag })
            val openingHours = tags.optString("opening_hours").takeIf { it.isNotBlank() }

            stations.add(
                GasStation(
                    id = "osm-$osmType-$osmId",
                    name = nameTag.ifBlank { brandTag.ifBlank { "İsimsiz akaryakıt istasyonu" } },
                    brand = brand,
                    latitude = lat,
                    longitude = lon,
                    highway = tags.optString("addr:street"),
                    city = tags.optString("addr:city").ifBlank { tags.optString("addr:district") },
                    hasLpg = tags.yesNo("fuel:lpg"),
                    hasDiesel = tags.yesNo("fuel:diesel"),
                    hasGasoline = tags.anyYesNo(GASOLINE_TAGS),
                    hasTasitTanima = false,
                    hasMarket = tags.yesNo("shop") ?: tags.yesNo("fuel:shop"),
                    hasRestaurant = tags.yesNo("restaurant"),
                    is24Hours = openingHours?.let { it.contains("24/7") },
                    osmType = osmType,
                    osmId = osmId,
                    openingHours = openingHours
                )
            )
        }
        return stations
    }

    /** Rotayı yaklaşık [stepKm] aralıklarla seyreltir; ilk ve son nokta korunur. */
    private fun downsample(points: List<LatLng>, stepKm: Double): List<LatLng> {
        if (points.size <= 2) return points
        val result = mutableListOf(points.first())
        var accumulated = 0.0
        for (i in 1 until points.size - 1) {
            accumulated += GeoUtils.distanceKm(points[i - 1], points[i])
            if (accumulated >= stepKm) {
                result.add(points[i])
                accumulated = 0.0
            }
        }
        result.add(points.last())
        return result
    }

    private fun JSONObject.yesNo(key: String): Boolean? = when (optString(key).lowercase()) {
        "" -> null
        "no", "false" -> false
        else -> true
    }

    private fun JSONObject.anyYesNo(keys: List<String>): Boolean? {
        var sawExplicitNo = false
        for (key in keys) {
            when (yesNo(key)) {
                true -> return true
                false -> sawExplicitNo = true
                null -> Unit
            }
        }
        return if (sawExplicitNo) false else null
    }

    companion object {
        private const val USER_AGENT = "YakitRotam/1.0 (Android; OSM fuel station lookup)"
        private val GASOLINE_TAGS = listOf(
            "fuel:octane_95", "fuel:octane_91", "fuel:octane_98", "fuel:octane_100", "fuel:e10"
        )
        // kumi.systems uzun koridor sorgularında belirgin şekilde hızlı; ana sunucu 504 verebiliyor.
        val DEFAULT_ENDPOINTS = listOf(
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass-api.de/api/interpreter"
        )
    }
}
