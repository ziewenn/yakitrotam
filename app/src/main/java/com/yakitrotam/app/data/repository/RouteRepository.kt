package com.yakitrotam.app.data.repository

import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.data.model.RoadDetour
import com.yakitrotam.app.data.model.RoutePath
import com.yakitrotam.app.util.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class RouteRepository {

    private val httpClient = OkHttpClient.Builder()
        // Uzun rotalarda (overview=full, binlerce nokta) mobil ağda 6 sn yetmiyordu ve
        // sessizce düz çizgi yedeğe düşülüyordu; istasyonlar da yanlış koridordan aranıyordu.
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    val popularCities: List<CityLocation> = listOf(
        CityLocation("İstanbul (Kadıköy / Anadolu)", "İstanbul", 40.9912, 29.0276),
        CityLocation("İstanbul (Beşiktaş / Avrupa)", "İstanbul", 41.0422, 29.0067),
        CityLocation("Ankara (Çankaya / Kızılay)", "Ankara", 39.9208, 32.8541),
        CityLocation("İzmir (Konak / Alsancak)", "İzmir", 38.4237, 27.1428),
        CityLocation("Bursa (Nilüfer)", "Bursa", 40.2123, 28.9812),
        CityLocation("Antalya (Muratpaşa)", "Antalya", 36.8841, 30.7056),
        CityLocation("Bodrum (Muğla)", "Muğla", 37.0344, 27.4305),
        CityLocation("Fethiye (Muğla)", "Muğla", 36.6217, 29.1164),
        CityLocation("Eskişehir (Tepebaşı)", "Eskişehir", 39.7767, 30.5206),
        CityLocation("Balıkesir (Merkez)", "Balıkesir", 39.6484, 27.8826),
        CityLocation("Aydın (Merkez)", "Aydın", 37.8560, 27.8416),
        CityLocation("Denizli (Pamukkale)", "Denizli", 37.7765, 29.0864),
        CityLocation("Konya (Selçuklu)", "Konya", 37.8746, 32.4932),
        CityLocation("Adana (Seyhan)", "Adana", 37.0000, 35.3213),
        CityLocation("Gaziantep (Şehitkamil)", "Gaziantep", 37.0662, 37.3833),
        CityLocation("Samsun (İlkadım)", "Samsun", 41.2867, 36.3300),
        CityLocation("Edirne (Merkez)", "Edirne", 41.6772, 26.5557)
    )

    /**
     * İki koordinat arasında rota çizgisi noktalarını ve mesafesini çeker.
     * İnternet varsa OSRM API'den gerçek sürüş rotasını alır.
     * Başarısız olursa veya çevrimdışıysa akıllı otoyol koridoru fallback'ini kullanır.
     */
    suspend fun getRoute(start: LatLng, end: LatLng): RoutePath = withContext(Dispatchers.IO) {
        try {
            val osrmUrl = "${Endpoints.osrm}/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
            val request = Request.Builder().url(osrmUrl).build()
            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val routes = json.optJSONArray("routes")
                    if (routes != null && routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val geometry = route.getJSONObject("geometry")
                        val coordinates = geometry.getJSONArray("coordinates")
                        val points = mutableListOf<LatLng>()
                        for (i in 0 until coordinates.length()) {
                            val coord = coordinates.getJSONArray(i)
                            val lng = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            points.add(LatLng(lat, lng))
                        }
                        if (points.isNotEmpty()) {
                            val durationMinutes = route.optDouble("duration").takeIf { it > 0 }?.div(60.0)
                            return@withContext RoutePath(points, durationMinutes)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ağ hatası durumunda fallback mekanizması devreye girer
        }

        return@withContext RoutePath(generateCorridorFallback(start, end))
    }

    private val detourCache = object : LinkedHashMap<String, List<RoadDetour?>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RoadDetour?>>?) = size > 24
    }

    /**
     * Her istasyon için "[from] → istasyon → [to]" ile doğrudan "[from] → [to]" arasındaki
     * gerçek yol farkını OSRM tablo servisinden tek istekte alır.
     *
     * Kuş uçuşu mesafe burada yanıltıcıdır: otoyola 100 m uzaklıktaki bir istasyon, otoyoldan
     * çıkıp geri dönmeyi gerektirdiği için 15-30 km ek yol anlamına gelebilir.
     *
     * Engelleyici çağrıdır; arka plan iş parçacığından çağrılmalıdır. Servise ulaşılamazsa
     * null döner ve çağıran taraf kuş uçuşu tahmine geri düşer.
     */
    fun roadDetours(from: LatLng, to: LatLng, stations: List<LatLng>): List<RoadDetour?>? {
        if (stations.isEmpty()) return emptyList()
        val coordinates = (listOf(from, to) + stations).joinToString(";") {
            String.format(Locale.US, "%.6f,%.6f", it.longitude, it.latitude)
        }
        synchronized(detourCache) { detourCache[coordinates] }?.let { return it }

        return try {
            val url = "${Endpoints.osrm}/table/v1/driving/$coordinates?annotations=duration,distance"
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())
                if (json.optString("code") != "Ok") return null
                val distances = json.getJSONArray("distances")
                val durations = json.getJSONArray("durations")
                fun cell(matrix: org.json.JSONArray, row: Int, column: Int): Double? =
                    matrix.getJSONArray(row).let { if (it.isNull(column)) null else it.getDouble(column) }

                val directMeters = cell(distances, 0, 1) ?: return null
                val directSeconds = cell(durations, 0, 1) ?: return null
                val result = stations.indices.map { index ->
                    val k = index + 2
                    val meters = (cell(distances, 0, k) ?: return@map null) + (cell(distances, k, 1) ?: return@map null)
                    val seconds = (cell(durations, 0, k) ?: return@map null) + (cell(durations, k, 1) ?: return@map null)
                    RoadDetour(
                        extraKm = ((meters - directMeters) / 1000.0).coerceAtLeast(0.0),
                        extraMinutes = ((seconds - directSeconds) / 60.0).coerceAtLeast(0.0)
                    )
                }
                synchronized(detourCache) { detourCache[coordinates] = result }
                result
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * O-4, O-5, O-21 gibi Türkiye ana otoyollarındaki geçiş noktalarını içeren gerçekçi yedek rota üretir.
     */
    fun generateCorridorFallback(start: LatLng, end: LatLng): List<LatLng> {
        val waypoints = mutableListOf<LatLng>()
        waypoints.add(start)

        // İstanbul -> Ankara aksı
        if ((start.latitude > 40.5 && start.longitude < 30.0 && end.latitude in 39.0..40.5 && end.longitude > 32.0) ||
            (end.latitude > 40.5 && end.longitude < 30.0 && start.latitude in 39.0..40.5 && start.longitude > 32.0)) {
            val highwayNodes = listOf(
                LatLng(40.8123, 29.4678), // Gebze
                LatLng(40.7654, 29.9812), // İzmit
                LatLng(40.7412, 30.4321), // Adapazarı
                LatLng(40.8234, 31.1432), // Düzce
                LatLng(40.7512, 31.4789), // Bolu Dağı
                LatLng(40.7812, 32.2541), // Gerede
                LatLng(40.4812, 32.4512), // Çamlıdere
                LatLng(40.1123, 32.6123)  // Akıncı / Ankara Girişi
            )
            if (start.longitude < end.longitude) {
                waypoints.addAll(highwayNodes)
            } else {
                waypoints.addAll(highwayNodes.reversed())
            }
        }
        // İstanbul -> İzmir aksı (O-5 Otoyolu)
        else if ((start.latitude > 40.5 && start.longitude in 28.5..30.0 && end.latitude in 38.0..39.0 && end.longitude in 26.5..28.0) ||
                 (end.latitude > 40.5 && end.longitude in 28.5..30.0 && start.latitude in 38.0..39.0 && start.longitude in 26.5..28.0)) {
            val o5Nodes = listOf(
                LatLng(40.6654, 29.5123), // Osmangazi Köprüsü
                LatLng(40.4812, 29.3212), // Orhangazi
                LatLng(40.2123, 28.7890), // Bursa Batı
                LatLng(39.9123, 28.1654), // Susurluk
                LatLng(39.6484, 27.8826), // Balıkesir
                LatLng(39.0123, 27.7812), // Akhisar
                LatLng(38.6123, 27.4312)  // Manisa
            )
            if (start.latitude > end.latitude) {
                waypoints.addAll(o5Nodes)
            } else {
                waypoints.addAll(o5Nodes.reversed())
            }
        }
        // İstanbul -> Antalya aksı (Bursa - Denizli - Korkuteli üzerinden)
        else if ((start.latitude > 40.5 && end.latitude < 37.5 && end.longitude in 30.0..32.0) ||
                 (end.latitude > 40.5 && start.latitude < 37.5 && start.longitude in 30.0..32.0)) {
            val antalyaNodes = listOf(
                LatLng(40.6654, 29.5123), // Osmangazi
                LatLng(40.2123, 28.9812), // Bursa
                LatLng(39.7767, 30.5206), // Eskişehir / Kütahya geçişi
                LatLng(38.7567, 30.5432), // Afyonkarahisar
                LatLng(37.8712, 30.5541), // Burdur
                LatLng(37.0654, 30.1987)  // Korkuteli
            )
            if (start.latitude > end.latitude) {
                waypoints.addAll(antalyaNodes)
            } else {
                waypoints.addAll(antalyaNodes.reversed())
            }
        }
        // Ankara -> Adana aksı (O-21 Niğde Otoyolu)
        else if ((start.latitude in 39.0..40.5 && start.longitude in 32.0..33.5 && end.latitude < 37.5 && end.longitude in 34.5..36.0) ||
                 (end.latitude in 39.0..40.5 && end.longitude in 32.0..33.5 && start.latitude < 37.5 && start.longitude in 34.5..36.0)) {
            val o21Nodes = listOf(
                LatLng(39.7712, 32.8123), // Gölbaşı
                LatLng(38.9412, 33.5412), // Şereflikoçhisar
                LatLng(38.4812, 33.9812), // Aksaray
                LatLng(37.8912, 34.6123), // Niğde
                LatLng(37.4212, 34.8712)  // Pozantı / Toroslar
            )
            if (start.latitude > end.latitude) {
                waypoints.addAll(o21Nodes)
            } else {
                waypoints.addAll(o21Nodes.reversed())
            }
        }
        // Genel interpolasyon (ara bağlantılar)
        else {
            val steps = 8
            for (i in 1 until steps) {
                val fraction = i.toDouble() / steps
                val lat = start.latitude + fraction * (end.latitude - start.latitude)
                val lng = start.longitude + fraction * (end.longitude - start.longitude)
                waypoints.add(LatLng(lat, lng))
            }
        }

        waypoints.add(end)

        // Noktalar arası yumuşatma / yoğunlaştırma
        val densePoints = mutableListOf<LatLng>()
        for (i in 0 until waypoints.size - 1) {
            val p1 = waypoints[i]
            val p2 = waypoints[i + 1]
            densePoints.add(p1)
            val segDist = GeoUtils.distanceKm(p1, p2)
            val subSegments = (segDist / 12.0).toInt().coerceAtLeast(1)
            for (j in 1 until subSegments) {
                val f = j.toDouble() / subSegments
                densePoints.add(LatLng(
                    p1.latitude + f * (p2.latitude - p1.latitude),
                    p1.longitude + f * (p2.longitude - p1.longitude)
                ))
            }
        }
        densePoints.add(waypoints.last())
        return densePoints
    }
}
