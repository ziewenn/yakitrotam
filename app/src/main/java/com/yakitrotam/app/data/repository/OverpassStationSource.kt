package com.yakitrotam.app.data.repository

import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.GasStation
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.util.GeoUtils
import com.yakitrotam.app.util.Provinces
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.cos

/**
 * Akaryakıt istasyonlarını OpenStreetMap verisinden getirir. Ücretsiz ve anahtarsızdır;
 * Google Places Nearby Search'ün yerini alır.
 *
 * Asıl kaynak, tüm Türkiye'yi kapsayan ve haftalık yenilenen hazır listedir
 * ([Endpoints.stations], `tools/build_stations.py` ile üretilir). Herkese açık Overpass
 * sunucuları sık sık meşgul ya da kapalı olduğu için yalnızca bu listeye ulaşılamazsa
 * canlı sorguya düşülür.
 *
 * Veriler ODbL lisanslıdır, kaynak gösterimi RouteSummaryScreen'de yapılır.
 */
class OverpassStationSource(
    /** null ise güncel sunucu listesi her sorguda [Endpoints]'ten okunur. */
    private val fixedEndpoints: List<String>? = null,
    /** Hazır listenin diskteki kopyası; null ise yalnızca bellekte tutulur. */
    private val datasetCache: File? = null,
    /** Hazır listenin adresi; şarj istasyonları için [Endpoints.chargers]. */
    private val datasetUrl: () -> String = { Endpoints.stations }
) {
    private val endpoints: List<String>
        get() = fixedEndpoints ?: Endpoints.overpass

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .build()

    private var dataset: List<GasStation>? = null

    /**
     * Tüm Türkiye istasyon listesi: bellekten, yoksa diskteki güncel kopyadan, o da yoksa ağdan.
     * İndirilemezse diskteki eski kopya kullanılır; hiçbiri yoksa null döner.
     */
    @Synchronized
    private fun loadDataset(): List<GasStation>? {
        dataset?.let { return it }

        fun parse(body: String?): List<GasStation>? = body
            ?.let { runCatching { parseElements(it) }.getOrNull() }
            ?.takeIf { it.size >= MIN_DATASET_STATIONS }

        fun readCache(): List<GasStation>? = parse(runCatching { datasetCache?.readText() }.getOrNull())

        val cacheIsFresh = datasetCache != null && datasetCache.exists() &&
            System.currentTimeMillis() - datasetCache.lastModified() < DATASET_TTL_MILLIS
        if (cacheIsFresh) readCache()?.let { dataset = it; return it }

        val body = try {
            val request = Request.Builder().url(datasetUrl()).header("User-Agent", USER_AGENT).build()
            httpClient.newBuilder().readTimeout(20, TimeUnit.SECONDS).build()
                .newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
        } catch (e: IOException) {
            Log.w("OverpassStationSource", "Hazır istasyon listesi indirilemedi", e)
            null
        }
        val downloaded = parse(body)
        if (downloaded != null) runCatching { datasetCache?.writeText(body!!) }

        return (downloaded ?: readCache()).also { dataset = it }
    }

    /** Hazır listenin tamamı (harita ekranı için); listeye ulaşılamazsa null. */
    suspend fun allStations(): List<GasStation>? = withContext(Dispatchers.IO) { loadDataset() }

    /**
     * Rota koridorundaki tüm istasyonları getirir: önce hazır listeden, ona ulaşılamazsa canlı
     * Overpass sorgusuyla. Kaynaklara hiç ulaşılamadıysa null döner; boş liste "bu rotada istasyon
     * yok" demektir (canlı yedeği olmayan şarj kaynağında olabilir).
     */
    suspend fun fetchAlongRoute(routePoints: List<LatLng>): List<GasStation>? {
        if (routePoints.isEmpty()) return emptyList()

        val dataset = withContext(Dispatchers.IO) { loadDataset() }
        if (dataset != null) {
            val corridor = stationsInCorridor(dataset, routePoints)
            // Şarj istasyonlarının canlı yedeği yok (OSM'de Türkiye'nin şarj verisi çok eksik):
            // liste geldiyse koridorun boş olması gerçek sonuçtur.
            if (corridor.isNotEmpty() || endpoints.isEmpty()) return corridor
        }
        if (endpoints.isEmpty()) return null

        val query = buildCorridorQuery(routePoints)
        // Aynı rota farklı depo seviyesiyle yeniden hesaplanınca ağa hiç çıkılmaz.
        synchronized(this) {
            if (query == lastQuery && lastStations.isNotEmpty()) return lastStations
        }

        // Sunucular "meşgul" (504) dönebiliyor; kısa bir aradan sonra çoğu zaman cevap veriyorlar.
        var stations = queryOverpass(query)
        if (stations.isEmpty()) {
            delay(1500)
            stations = queryOverpass(query)
        }

        if (stations.isEmpty()) return null
        synchronized(this) {
            lastQuery = query
            lastStations = stations
        }
        return stations
    }

    /**
     * Sunucular aynı anda sorgulanır, ilk dolu cevap kullanılır: Overpass
     * sunucularından biri yoğunken diğeri çoğu zaman saniyeler içinde cevap verir.
     */
    private suspend fun queryOverpass(query: String): List<GasStation> {
        val calls = endpoints.map { endpoint ->
            httpClient.newCall(
                Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", USER_AGENT)
                    .post(FormBody.Builder().add("data", query).build())
                    .build()
            )
        }

        return try {
            // Sunucular aynı anda sorgulanır, ilk dolu cevap kazanır. Kaybedenler
            // Call.cancel() ile anında kapatılır: engelleyici execute() + coroutine
            // iptali soket okumasını kesmediği için eskiden yavaş sunucunun 60+ sn'lik
            // zaman aşımı bekleniyordu ve rota hesabı ~50 sn sürüyordu.
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { calls.forEach(Call::cancel) }
                var remaining = calls.size

                fun finish(result: List<GasStation>) = synchronized(calls) {
                    remaining--
                    if (continuation.isActive && (result.isNotEmpty() || remaining == 0)) {
                        continuation.resume(result)
                    }
                }

                calls.forEach { call ->
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) = finish(emptyList())

                        override fun onResponse(call: Call, response: Response) {
                            val result = runCatching {
                                response.use {
                                    if (!it.isSuccessful) emptyList()
                                    else parseElements(it.body?.string().orEmpty())
                                }
                            }.getOrDefault(emptyList())
                            finish(result)
                        }
                    })
                }
            }
        } finally {
            calls.forEach(Call::cancel)
        }
    }

    private var lastQuery: String? = null
    private var lastStations: List<GasStation> = emptyList()

    internal fun parseElements(body: String): List<GasStation> {
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
                    name = nameTag.ifBlank { brandTag.ifBlank { GasStation.UNNAMED } },
                    brand = brand,
                    latitude = lat,
                    longitude = lon,
                    highway = tags.optString("addr:street"),
                    // OSM'de adres çoğu istasyonda boş; kartta "154. km" yalnız kalmasın diye ile düşülür.
                    city = tags.optString("addr:city").ifBlank { tags.optString("addr:district") }
                        .ifBlank { Provinces.nearest(LatLng(lat, lon)).displayName },
                    hasLpg = tags.yesNo("fuel:lpg"),
                    hasDiesel = tags.yesNo("fuel:diesel"),
                    hasGasoline = tags.anyYesNo(GASOLINE_TAGS),
                    hasTasitTanima = false,
                    hasMarket = tags.yesNo("shop") ?: tags.yesNo("fuel:shop"),
                    hasRestaurant = tags.yesNo("restaurant"),
                    is24Hours = openingHours?.let { it.contains("24/7") },
                    osmType = osmType,
                    osmId = osmId,
                    openingHours = openingHours,
                    dataSource = if (osmType == "epdk") "EPDK" else "OpenStreetMap",
                    ccsKw = tags.positiveDouble("charge:ccs_kw"),
                    chademoKw = tags.positiveDouble("charge:chademo_kw"),
                    chargeInfo = tags.optString("charge:info").takeIf { it.isNotBlank() },
                    kwhPriceTL = tags.positiveDouble("charge:price")
                )
            )
        }
        return stations
    }

    private fun JSONObject.positiveDouble(key: String): Double? =
        optString(key).replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

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
        ): String = corridorBoxes(routePoints, chunkKm, paddingKm).joinToString(
            separator = "\n",
            prefix = "[out:json][timeout:60];\n(\n",
            postfix = "\n);\nout center tags;"
        ) { box ->
            String.format(
                Locale.US,
                "nwr[\"amenity\"=\"fuel\"](%.5f,%.5f,%.5f,%.5f);",
                box.minLat, box.minLon, box.maxLat, box.maxLon
            )
        }

        /** Rota çevresindeki dikdörtgen; hem Overpass sorgusunda hem hazır listeyi süzmekte kullanılır. */
        internal class Box(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
            operator fun contains(station: GasStation): Boolean =
                station.latitude in minLat..maxLat && station.longitude in minLon..maxLon
        }

        internal fun corridorBoxes(
            routePoints: List<LatLng>,
            chunkKm: Double = 25.0,
            paddingKm: Double = 3.5
        ): List<Box> {
            val boxes = mutableListOf<Box>()
            var chunk = mutableListOf(routePoints.first())
            var accumulated = 0.0

            fun flush() {
                val minLat = chunk.minOf { it.latitude }
                val maxLat = chunk.maxOf { it.latitude }
                val latPad = paddingKm / 111.32
                val lonPad = paddingKm / (111.32 * cos(Math.toRadians((minLat + maxLat) / 2.0)))
                boxes.add(
                    Box(
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
            return boxes
        }

        /** [stations] içinden rota koridoruna düşenler; canlı Overpass sorgusuyla aynı sonucu verir. */
        internal fun stationsInCorridor(stations: List<GasStation>, routePoints: List<LatLng>): List<GasStation> {
            val boxes = corridorBoxes(routePoints)
            return stations.filter { station -> boxes.any { station in it } }
        }

        /** Bundan az kayıt içeren liste yarım inmiş sayılır ve kullanılmaz. */
        private const val MIN_DATASET_STATIONS = 1000
        private const val DATASET_TTL_MILLIS = 3 * 24 * 60 * 60 * 1000L

        private const val USER_AGENT = "YakitRotam/1.0 (Android; OSM fuel station lookup)"
        private val GASOLINE_TAGS = listOf(
            "fuel:octane_95", "fuel:octane_91", "fuel:octane_98", "fuel:octane_100", "fuel:e10"
        )
    }
}
