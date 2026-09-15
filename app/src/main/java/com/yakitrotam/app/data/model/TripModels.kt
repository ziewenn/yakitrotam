package com.yakitrotam.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CityLocation(
    val name: String,
    val province: String,
    val latitude: Double,
    val longitude: Double
) {
    val latLng: LatLng
        get() = LatLng(latitude, longitude)
}

enum class PlaceSource { GOOGLE, OPEN_STREET_MAP, LOCAL }

data class PlaceSuggestion(
    val id: String,
    val title: String,
    val subtitle: String,
    val source: PlaceSource,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class FuelStop(
    val stopIndex: Int,
    val station: GasStation,
    val distanceFromOriginKm: Double,
    val legDistanceKm: Double,
    val arrivalFuelLevelPercent: Double,  // İstasyon kapısına varıldığında depoda kalan yakıt %
    val arrivalFuelLiters: Double,        // Kalan yakıt Litre
    val refuelLiters: Double,             // Depoyu tam doldurmak için gereken litre
    val estimatedRefuelCostTL: Double,    // Yaklaşık dolum maliyeti (TL)
    val detourDistanceKm: Double          // Ana güzergahtan sapma mesafesi (km)
)

@Serializable
data class TripPlanResult(
    val origin: CityLocation,
    val destination: CityLocation,
    val totalDistanceKm: Double,
    val estimatedDrivingTimeMinutes: Int,
    val stops: List<FuelStop>,
    val totalFuelConsumedLiters: Double,
    val totalEstimatedCostTL: Double,
    val routePoints: List<LatLng>,
    val vehicleProfile: VehicleProfile,
    val preferredBrands: Set<FuelBrand>
) {
    val stopsCount: Int
        get() = stops.size

    val hasStops: Boolean
        get() = stops.isNotEmpty()

    val googleMapsUrl: String
        get() {
            val originStr = "${origin.latitude},${origin.longitude}"
            val destStr = "${destination.latitude},${destination.longitude}"
            val waypointsStr = stops.joinToString("|") { "${it.station.latitude},${it.station.longitude}" }
            
            return if (waypointsStr.isNotEmpty()) {
                "https://www.google.com/maps/dir/?api=1&origin=$originStr&destination=$destStr&waypoints=$waypointsStr&travelmode=driving"
            } else {
                "https://www.google.com/maps/dir/?api=1&origin=$originStr&destination=$destStr&travelmode=driving"
            }
        }
}
