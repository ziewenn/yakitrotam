package com.yakitrotam.app.data.repository

import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.GasStation
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.util.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.selects.select
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos

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
        .readTimeout(70, TimeUnit.SECONDS)
        .build()

    /**
     * Rota koridorundaki tüm akaryakıt istasyonlarını getirir.
     *
     * Sunucular aynı anda sorgulanır, ilk dolu cevap kullanılır: Overpass
     * sunucularından biri yoğunken diğeri çoğu zaman saniyeler içinde cevap verir.
     */
    suspend fun fetchAlongRoute(routePoints: List<LatLng>): List<GasStation> {
        if (routePoints.isEmpty()) return emptyList()
        val query = buildCorridorQuery(routePoints)

        return coroutineScope {
            val pending = endpoints.map { endpoint ->
                async {
                    runCatching {
                        runInterruptible(Dispatchers.IO) { requestStations(endpoint, query) }
                    }.getOrDefault(emptyList())
                }
            }.toMutableList()

            while (pending.isNotEmpty()) {
                val (job, stations) = select {
                    pending.forEach { job -> job.onAwait { job to it } }
                }
                pending.remove(job)
                if (stations.isNotEmpty()) {
                    pending.forEach { it.cancel() }
                    return@coroutineScope stations
                }
            }
            emptyList()
        }
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
        /**
         * Rotayı ~[chunkKm] km'lik parçalara bölüp her parçanın sınır kutusunu
         * [paddingKm] genişleterek tek bir birleşik sorgu üretir.
         *
         * Eskiden tek bir `around:` sorgusu kullanılıyordu; 700 km'lik rotada
         * (ör. Gebze-Altunhisar) iki sunucuda da 60 sn'de zaman aşımına düşüyordu.
         * Kutu sorguları mekânsal indeksi kullanır, aynı rota ~7 sn'de döner.
         * Kutuların fazladan yakaladığı istasyonları motor sapma mesafesiyle eler.
         */
        internal fun buildCorridorQuery(
            routePoints: List<LatLng>,
            chunkKm: Double = 25.0,
            paddingKm: Double = 3.5
        ): String {
            val boxes = mutableListOf<String>()
            var chunk = mutableListOf(routePoints.first())
            var accumulated = 0.0

            fun flush() {
                val minLat = chunk.minOf { it.latitude }
                val maxLat = chunk.maxOf { it.latitude }
                val latPad = paddingKm / 111.32
                val lonPad = paddingKm / (111.32 * cos(Math.toRadians((minLat + maxLat) / 2.0)))
                boxes.add(
                    String.format(
                        Locale.US,
                        "nwr[\"amenity\"=\"fuel\"](%.5f,%.5f,%.5f,%.5f);",
                        minLat - latPad,
                        chunk.minOf { it.longitude } - lonPad,
                        maxLat + latPad,
                        chunk.maxOf { it.longitude } + lonPad
                    )
                )
            }

            for (i in 1 until routePoints.size) {
                accumulated += GeoUtils.distanceKm(routePoints[i - 1], routePoints[i])
                chunk.add(routePoints[i])
                if (accumulated >= chunkKm) {
                    flush()
                    chunk = mutableListOf(routePoints[i])
                    accumulated = 0.0
                }
            }
            if (chunk.size > 1 || boxes.isEmpty()) flush()

            return boxes.joinToString(
                separator = "\n",
                prefix = "[out:json][timeout:60];\n(\n",
                postfix = "\n);\nout center tags;"
            )
        }

        private const val USER_AGENT = "YakitRotam/1.0 (Android; OSM fuel station lookup)"
        private val GASOLINE_TAGS = listOf(
            "fuel:octane_95", "fuel:octane_91", "fuel:octane_98", "fuel:octane_100", "fuel:e10"
        )
        val DEFAULT_ENDPOINTS = listOf(
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass-api.de/api/interpreter"
        )
    }
}
