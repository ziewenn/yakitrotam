package com.yakitrotam.app.util

import com.yakitrotam.app.data.model.LatLng
import kotlin.math.*

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
                val interpLat = points[i].latitude + fraction * (points[i + 1].latitude - points[i].latitude)
                val interpLng = points[i].longitude + fraction * (points[i + 1].longitude - points[i].longitude)
                return LatLng(interpLat, interpLng)
            }
            accumulated += segmentDist
        }
        return points.last()
    }

    /**
     * Bir noktanın bir doğru parçasına (rota segmentine) olan en kısa mesafesini (cross-track distance) yaklaşık hesaplar.
     */
    fun distanceToSegmentKm(point: LatLng, segStart: LatLng, segEnd: LatLng): Double {
        val l2 = distanceKm(segStart, segEnd).pow(2)
        if (l2 == 0.0) return distanceKm(point, segStart)

        // İzdüşüm faktörü t
        val t = (((point.latitude - segStart.latitude) * (segEnd.latitude - segStart.latitude) +
                (point.longitude - segStart.longitude) * (segEnd.longitude - segStart.longitude)) /
                ((segEnd.latitude - segStart.latitude).pow(2) + (segEnd.longitude - segStart.longitude).pow(2)))
            .coerceIn(0.0, 1.0)

        val projLat = segStart.latitude + t * (segEnd.latitude - segStart.latitude)
        val projLng = segStart.longitude + t * (segEnd.longitude - segStart.longitude)
        return distanceKm(point, LatLng(projLat, projLng))
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
            if (d < minDistance) {
                minDistance = d
            }
        }
        return minDistance
    }
}
