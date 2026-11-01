package com.yakitrotam.app.domain

import com.yakitrotam.app.data.model.LatLng
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Rotanın bir yol numarası (O-5) üzerinde giden parçası; konum parçanın başlangıcıdır. */
data class RefSegment(val ref: String, val start: LatLng, val km: Double)

@kotlinx.serialization.Serializable
data class TollItem(val name: String, val priceTL: Double)

/** Otomobil (1. sınıf) için tahmini köprü, tünel ve otoyol ücreti. */
@kotlinx.serialization.Serializable
data class TollEstimate(val items: List<TollItem>) {
    val totalTL: Double get() = items.sumOf { it.priceTL }
}

/**
 * KGM 2026 tarifeleri (1. sınıf, KDV dahil). Köprü ve tüneller noktadan, otoyollar rotanın o yol
 * numarası üzerinde gittiği km'ye oranla hesaplanır (uç uca ücreti aşmaz).
 * ponytail: gişe matrisi yerine km oranı; giriş-çıkış gişesine göre kesin ücret gerekirse
 * gişe koordinatları OSM'den çıkarılıp matris eklenmeli. Tarifeler yılda iki kez (Ocak, Temmuz) değişir.
 */
object Tolls {
    const val TARIFF_DATE = "Temmuz 2026"

    private class Crossing(val name: String, val lat: Double, val lon: Double, val radiusM: Double, val price: Double)

    private class Motorway(
        val name: String, val ref: String, val price: Double, val lengthKm: Double,
        val minLat: Double = -90.0, val maxLat: Double = 90.0,
        val minLon: Double = -180.0, val maxLon: Double = 180.0
    ) {
        fun covers(p: LatLng) = p.latitude in minLat..maxLat && p.longitude in minLon..maxLon
    }

    private val crossings = listOf(
        Crossing("15 Temmuz Şehitler Köprüsü", 41.04558, 29.03423, 200.0, 59.0),
        Crossing("Fatih Sultan Mehmet Köprüsü", 41.09137, 29.06109, 200.0, 59.0),
        Crossing("Yavuz Sultan Selim Köprüsü", 41.20297, 29.11164, 250.0, 110.0),
        Crossing("Osmangazi Köprüsü", 40.75282, 29.51543, 300.0, 1170.0),
        Crossing("1915 Çanakkale Köprüsü", 40.33897, 26.63798, 300.0, 1170.0),
        // Gündüz ücreti; gece (00-05) yarı fiyat. Kaynak ikincil, avrasyatuneli.com'dan doğrulanmalı.
        Crossing("Avrasya Tüneli", 41.00602, 28.99898, 300.0, 330.0)
    )

    private val motorways = listOf(
        Motorway("İstanbul–İzmir Otoyolu", "O-5", 1725.0, 400.0),
        Motorway("Malkara–Çanakkale Otoyolu", "O-6", 310.0, 100.0),
        Motorway("Ankara–Niğde Otoyolu", "O-21", 925.0, 275.0, minLat = 37.95),
        Motorway("Niğde–Tarsus Otoyolu", "O-21", 197.0, 165.0, maxLat = 37.95),
        Motorway("Kuzey Marmara Otoyolu (Avrupa)", "O-7", 260.0, 90.0, maxLon = 28.75),
        Motorway("Kuzey Marmara Otoyolu (Kuzey Çevre)", "O-7", 600.0, 60.0, minLon = 28.75, maxLon = 29.35),
        Motorway("Kuzey Marmara Otoyolu (Anadolu)", "O-7", 600.0, 125.0, minLon = 29.35),
        Motorway("Anadolu Otoyolu", "O-4", 338.0, 391.0),
        Motorway("Avrupa Otoyolu", "O-3", 168.0, 212.0),
        Motorway("İzmir–Aydın Otoyolu", "O-31", 73.0, 80.0, maxLon = 27.85),
        Motorway("Aydın–Denizli Otoyolu", "O-31", 590.0, 125.0, minLon = 27.85),
        Motorway("İzmir–Çeşme Otoyolu", "O-32", 53.0, 56.0),
        Motorway("Menemen–Çandarlı Otoyolu", "O-33", 240.0, 60.0),
        Motorway("Mersin–Adana Otoyolu", "O-51", 55.0, 70.0),
        Motorway("Adana–Gaziantep Otoyolu", "O-52", 102.0, 200.0, maxLon = 37.4),
        Motorway("Gaziantep–Şanlıurfa Otoyolu", "O-52", 102.0, 110.0, minLon = 37.4)
    )

    /** Rampa ve kavşak parçaları ücret sayılmasın. */
    private const val MIN_MOTORWAY_KM = 2.0

    fun estimate(points: List<LatLng>, segments: List<RefSegment>): TollEstimate? {
        val items = mutableListOf<TollItem>()
        crossings.filter { c -> distanceToRouteM(points, c.lat, c.lon) <= c.radiusM }
            .forEach { items += TollItem(it.name, it.price) }

        val kmByMotorway = HashMap<Motorway, Double>()
        for (segment in segments) {
            val refs = segment.ref.split(';').map { it.trim() }
            val motorway = motorways.firstOrNull { it.ref in refs && it.covers(segment.start) } ?: continue
            kmByMotorway[motorway] = (kmByMotorway[motorway] ?: 0.0) + segment.km
        }
        motorways.forEach { m ->
            val km = kmByMotorway[m] ?: return@forEach
            if (km >= MIN_MOTORWAY_KM) items += TollItem(m.name, Math.round(m.price * min(1.0, km / m.lengthKm)).toDouble())
        }
        return items.takeIf { it.isNotEmpty() }?.let(::TollEstimate)
    }

    /** Noktanın rota çizgisine en kısa uzaklığı (m). Köprüler uzun ve düz; tek tek noktalar yetmez. */
    private fun distanceToRouteM(points: List<LatLng>, lat: Double, lon: Double): Double {
        val kx = 111_320.0 * cos(Math.toRadians(lat))
        val ky = 110_540.0
        var best = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val ax = (points[i].longitude - lon) * kx
            val ay = (points[i].latitude - lat) * ky
            val bx = (points[i + 1].longitude - lon) * kx
            val by = (points[i + 1].latitude - lat) * ky
            val dx = bx - ax
            val dy = by - ay
            val lengthSq = dx * dx + dy * dy
            val t = if (lengthSq == 0.0) 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / lengthSq))
            val px = ax + t * dx
            val py = ay + t * dy
            best = min(best, sqrt(px * px + py * py))
        }
        return best
    }
}
