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

enum class PlaceSource { OPEN_STREET_MAP, LOCAL, RECENT }

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
    /** Durağın rotaya eklediği toplam yolun yarısı (gidiş payı); toplam ek yol bunun iki katıdır. */
    val detourDistanceKm: Double,
    /** Aynı bölgede, mevcut yakıtla ulaşılabilen ve bu durağın yerine seçilebilecek istasyonlar. */
    val alternatives: List<StopAlternative> = emptyList(),
    /** Gerçek yol ağından hesaplanan ek süre; yol verisi alınamadıysa null. */
    val detourMinutes: Double? = null,
    /** Durağın bulunduğu ildeki litre fiyatı; tutar bununla hesaplanır. */
    val pricePerLiterTL: Double = 0.0
) {
    val extraRoadKm: Double
        get() = detourDistanceKm * 2
}

@Serializable
data class StopAlternative(
    val station: GasStation,
    val distanceFromOriginKm: Double,
    val detourDistanceKm: Double,
    val detourMinutes: Double? = null
) {
    val extraRoadKm: Double
        get() = detourDistanceKm * 2
}

/** Rota çizgisi ve yol servisinin süre tahmini (yedek rotada süre yoktur). */
data class RoutePath(val points: List<LatLng>, val durationMinutes: Double? = null)

/** Bir istasyona uğramanın, doğrudan devam etmeye göre gerçek yol ağındaki ek maliyeti. */
data class RoadDetour(
    val extraKm: Double,
    val extraMinutes: Double
)

/** Aday istasyonların gerçek yol sapmasını çözer; ulaşılamazsa null döner (bkz. RouteRepository.roadDetours). */
fun interface DetourResolver {
    fun resolve(from: LatLng, to: LatLng, stations: List<LatLng>): List<RoadDetour?>?
}

@Serializable
data class TripPlanResult(
    val origin: CityLocation,
    val destination: CityLocation,
    val totalDistanceKm: Double,            // Ana güzergah uzunluğu
    val totalDrivenDistanceKm: Double,      // Duraklara sapmalar dahil gerçekte sürülen mesafe
    val estimatedDrivingTimeMinutes: Int,
    val stops: List<FuelStop>,
    val totalFuelConsumedLiters: Double,    // Sapmalar dahil yakılan toplam yakıt
    val totalEstimatedCostTL: Double,       // Yakılan yakıtın parasal karşılığı
    val totalRefuelCostTL: Double,          // Duraklarda pompada ödenecek toplam tutar
    val arrivalFuelLiters: Double,          // Varışta depoda kalan yakıt
    val routePoints: List<LatLng>,
    val vehicleProfile: VehicleProfile,
    val preferredBrands: Set<FuelBrand>,
    val fuelPrice: FuelPriceSnapshot,
    /** Menzil içinde uygun istasyon bulunamadıysa kullanıcıya gösterilecek uyarı. */
    val warning: String? = null,
    /** Yol servisinin verdiği duraksız sürüş süresi; alternatif seçilince yeniden kullanılır. */
    val routeDurationMinutes: Double? = null
) {
    val stopsCount: Int
        get() = stops.size

    val hasStops: Boolean
        get() = stops.isNotEmpty()

    val arrivalFuelPercent: Double
        get() = (arrivalFuelLiters / vehicleProfile.tankCapacityLiters.coerceAtLeast(1.0)) * 100.0

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
