package com.yakitrotam.app

import com.yakitrotam.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class GoogleMapsIntentTest {

    @Test
    fun testGoogleMapsUrl_GeneratesValidFormatWithWaypoints() {
        val origin = CityLocation("İstanbul", "İstanbul", 40.9912, 29.0276)
        val destination = CityLocation("Antalya", "Antalya", 36.8841, 30.7056)

        val station1 = GasStation(
            id = "s1",
            name = "Shell Susurluk",
            brand = FuelBrand.SHELL,
            latitude = 39.9123,
            longitude = 28.1654
        )

        val stop1 = FuelStop(
            stopIndex = 1,
            station = station1,
            distanceFromOriginKm = 180.0,
            legDistanceKm = 180.0,
            arrivalFuelLevelPercent = 14.0,
            arrivalFuelLiters = 7.0,
            refuelLiters = 43.0,
            estimatedRefuelCostTL = 1800.0,
            detourDistanceKm = 0.2
        )

        val tripResult = TripPlanResult(
            origin = origin,
            destination = destination,
            totalDistanceKm = 680.0,
            totalDrivenDistanceKm = 680.4,
            estimatedDrivingTimeMinutes = 440,
            stops = listOf(stop1),
            totalFuelConsumedLiters = 45.0,
            totalEstimatedCostTL = 3607.2,
            totalRefuelCostTL = 3446.9,
            arrivalFuelLiters = 12.0,
            routePoints = emptyList(),
            vehicleProfile = VehicleProfile(),
            preferredBrands = setOf(FuelBrand.SHELL),
            fuelPrice = FuelPriceSnapshot.fallback()
        )

        val url = tripResult.googleMapsUrl

        assertTrue("Google Maps API URL ile başlamalıdır", url.startsWith("https://www.google.com/maps/dir/?api=1"))
        assertTrue("Origin parametresi içermelidir", url.contains("origin=40.9912,29.0276"))
        assertTrue("Destination parametresi içermelidir", url.contains("destination=36.8841,30.7056"))
        assertTrue("Waypoints parametresi içermelidir", url.contains("waypoints=39.9123,28.1654"))
        assertTrue("Sürüş modu driving olmalıdır", url.contains("travelmode=driving"))
    }
}
