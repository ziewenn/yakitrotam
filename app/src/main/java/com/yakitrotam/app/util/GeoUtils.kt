package com.yakitrotam.app.util

import com.yakitrotam.app.data.model.LatLng
import kotlin.math.*

/** Bir noktanın rota üzerindeki izdüşümü: rota başından kaç km ileride ve rotadan kaç km uzakta. */
data class RouteProjection(
    val alongKm: Double,
    val detourKm: Double
)

object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Haversine formülü ile iki koordinat arasındaki kuş uçuşu mesafeyi (km) hesaplar.
     */
    fun distanceKm(start: LatLng, end: LatLng): Double {
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)

        val a = sin(dLat / 2).pow(2) + sin(dLon / 2).pow(2) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Bir rota çizgisi (polyline) üzerindeki toplam mesafeyi (km) hesaplar.
     */
    fun totalPolylineDistanceKm(points: List<LatLng>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += distanceKm(points[i], points[i + 1])
        }
        return total
    }

    /**
     * Rota noktalarının başlangıçtan itibaren kümülatif mesafelerini (km) döndürür.
     * Çok sayıda istasyonu aynı rotaya izdüşürürken bir kez hesaplanıp tekrar kullanılır.
     */
    fun cumulativeDistancesKm(points: List<LatLng>): DoubleArray {
        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + distanceKm(points[i - 1], points[i])
        }
        return cumulative
    }

    /**
     * Rota boyunca belirli bir kümülatif mesafeye (km) ulaşıldığındaki yaklaşık koordinatı bulur.
     */
    fun findPointAtDistance(points: List<LatLng>, targetDistanceKm: Double): LatLng {
        if (points.isEmpty()) return LatLng(0.0, 0.0)
        if (points.size == 1 || targetDistanceKm <= 0.0) return points.first()

        var accumulated = 0.0
        for (i in 0 until points.size - 1) {
            val segmentDist = distanceKm(points[i], points[i + 1])
            if (accumulated + segmentDist >= targetDistanceKm) {
                val fraction = if (segmentDist > 0) (targetDistanceKm - accumulated) / segmentDist else 0.0
                return interpolate(points[i], points[i + 1], fraction)
            }
            accumulated += segmentDist
        }
        return points.last()
    }

    fun interpolate(from: LatLng, to: LatLng, fraction: Double): LatLng = LatLng(
        from.latitude + fraction * (to.latitude - from.latitude),
        from.longitude + fraction * (to.longitude - from.longitude)
    )

    /**
     * Noktanın segment üzerindeki izdüşüm oranı (0..1).
     * Boylam dereceleri enleme göre daralır; km cinsine çevirmeden hesaplamak
     * Türkiye enlemlerinde (~39°) %22'ye varan izdüşüm hatası üretir.
     */
    private fun projectionFraction(point: LatLng, segStart: LatLng, segEnd: LatLng): Double {
        val lonScale = cos(Math.toRadians((segStart.latitude + segEnd.latitude) / 2.0))
        val dx = (segEnd.longitude - segStart.longitude) * lonScale
        val dy = segEnd.latitude - segStart.latitude
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return 0.0

        val px = (point.longitude - segStart.longitude) * lonScale
        val py = point.latitude - segStart.latitude
        return ((px * dx + py * dy) / lengthSquared).coerceIn(0.0, 1.0)
    }

    /**
     * Bir noktanın bir doğru parçasına (rota segmentine) olan en kısa mesafesi (km).
     */
    fun distanceToSegmentKm(point: LatLng, segStart: LatLng, segEnd: LatLng): Double {
        val t = projectionFraction(point, segStart, segEnd)
        return distanceKm(point, interpolate(segStart, segEnd, t))
    }

    /**
     * Bir noktanın tüm bir rota çizgisine olan minimum mesafesini (detour / sapma) hesaplar.
     */
    fun minDistanceToRouteKm(point: LatLng, route: List<LatLng>): Double {
        if (route.isEmpty()) return Double.MAX_VALUE
        if (route.size == 1) return distanceKm(point, route.first())

        var minDistance = Double.MAX_VALUE
        for (i in 0 until route.size - 1) {
            val d = distanceToSegmentKm(point, route[i], route[i + 1])
            if (d < minDistance) minDistance = d
        }
        return minDistance
    }

    /**
     * Noktayı rotaya izdüşürür: rotanın başından kaç km sonra geldiğini ve
     * rotadan kaç km saptığını birlikte döndürür.
     *
     * [cumulative] verilmezse rota için yeniden hesaplanır.
     */
    fun projectOntoRoute(
        point: LatLng,
        route: List<LatLng>,
        cumulative: DoubleArray = cumulativeDistancesKm(route)
    ): RouteProjection {
        if (route.isEmpty()) return RouteProjection(0.0, Double.MAX_VALUE)
        if (route.size == 1) return RouteProjection(0.0, distanceKm(point, route.first()))

        var bestDetour = Double.MAX_VALUE
        var bestAlong = 0.0

        for (i in 0 until route.size - 1) {
            val t = projectionFraction(point, route[i], route[i + 1])
            val projected = interpolate(route[i], route[i + 1], t)
            val detour = distanceKm(point, projected)
            if (detour < bestDetour) {
                bestDetour = detour
                val segmentLength = cumulative[i + 1] - cumulative[i]
                bestAlong = cumulative[i] + t * segmentLength
            }
        }
        return RouteProjection(bestAlong, bestDetour)
    }
}
