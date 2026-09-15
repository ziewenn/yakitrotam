package com.yakitrotam.app

import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.util.GeoUtils
import org.junit.Assert.*
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun testDistanceKm_BetweenIstanbulAndAnkara_IsApproximatelyRealistic() {
        // Istanbul (approx 41.0082, 28.9784), Ankara (39.9334, 32.8597)
        val istanbul = LatLng(41.0082, 28.9784)
        val ankara = LatLng(39.9334, 32.8597)

        val distance = GeoUtils.distanceKm(istanbul, ankara)

        // Kuş uçuşu yaklaşık 350 km olmalıdır
        assertTrue("Mesafe $distance km beklenenden farklı", distance in 340.0..360.0)
    }

    @Test
    fun testTotalPolylineDistance_CalculatesAccurately() {
        val points = listOf(
            LatLng(40.0, 30.0),
            LatLng(40.0, 31.0),
            LatLng(40.0, 32.0)
        )

        val total = GeoUtils.totalPolylineDistanceKm(points)
        assertTrue("Toplam polyline mesafesi $total km olmalı", total > 150.0)
    }

    @Test
    fun testFindPointAtDistance_InterpolatesCorrectly() {
        val start = LatLng(40.0, 30.0)
        val end = LatLng(40.0, 32.0)
        val points = listOf(start, end)

        val totalDist = GeoUtils.distanceKm(start, end)
        val midpoint = GeoUtils.findPointAtDistance(points, totalDist / 2.0)

        assertEquals(40.0, midpoint.latitude, 0.01)
        assertEquals(31.0, midpoint.longitude, 0.05)
    }

    @Test
    fun testMinDistanceToRoute_PointOnSegment_ReturnsNearZero() {
        val route = listOf(
            LatLng(40.0, 30.0),
            LatLng(40.0, 32.0)
        )
        val pointOnRoute = LatLng(40.0, 31.0)

        val dist = GeoUtils.minDistanceToRouteKm(pointOnRoute, route)
        assertTrue("Nokta rota üzerinde olduğu için mesafe ~0 olmalı: $dist", dist < 0.5)
    }
}
