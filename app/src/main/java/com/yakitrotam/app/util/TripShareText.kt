package com.yakitrotam.app.util

import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.model.TripPlanResult
import java.net.URLEncoder
import java.util.Locale

/**
 * Yakıt planını WhatsApp, SMS veya e-postaya yapıştırılabilecek düz metne çevirir.
 * Biçimlendirme işareti kullanılmaz; her uygulamada aynı okunur.
 */
object TripShareText {
    private val turkish: Locale = Locale.forLanguageTag("tr")

    fun build(trip: TripPlanResult): String = buildString {
        val fuelName = when (trip.vehicleProfile.fuelType) {
            FuelType.BENZIN -> "benzin"
            FuelType.DIZEL -> "motorin"
            FuelType.LPG -> "LPG"
        }
        val hours = trip.estimatedDrivingTimeMinutes / 60
        val minutes = trip.estimatedDrivingTimeMinutes % 60
        val duration = if (hours > 0) "$hours sa $minutes dk" else "$minutes dk"

        appendLine("YakıtRotam yakıt planı")
        appendLine("${trip.origin.name} → ${trip.destination.name}")
        append("${trip.totalDistanceKm.toInt()} km · yaklaşık $duration · ")
        appendLine(if (trip.hasStops) "${trip.stopsCount} yakıt molası" else "yakıt molası gerekmiyor")
        appendLine()

        trip.stops.forEach { stop ->
            val place = listOf(stop.station.city).filter(String::isNotBlank).joinToString()
            append("${stop.stopIndex}. ${stop.station.name}")
            if (place.isNotBlank()) append(" ($place)")
            appendLine(" · ${stop.distanceFromOriginKm.toInt()}. km")
            appendLine(
                "   Varışta %${stop.arrivalFuelLevelPercent.toInt()} kalır, " +
                    "${decimal(stop.refuelLiters)} L $fuelName, ~${money(stop.estimatedRefuelCostTL)}"
            )
            appendLine("   ${pointUrl(stop.station.latitude, stop.station.longitude)}")
        }

        if (trip.hasStops) {
            appendLine()
            appendLine("Pompada toplam: ~${money(trip.totalRefuelCostTL)}")
        } else {
            appendLine("Varışta depoda yaklaşık ${decimal(trip.arrivalFuelLiters)} L kalır.")
        }
        appendLine("Tüm rota ve duraklar: ${routeUrl(trip)}")
        appendLine()
        append("Fiyatlar tahminidir (${trip.fuelPrice.sourceName}, ${trip.fuelPrice.priceDate}).")
    }

    private fun decimal(value: Double) = String.format(turkish, "%.1f", value)

    private fun money(value: Double) = String.format(turkish, "%,.0f ₺", value)

    private fun pointUrl(lat: Double, lng: Double) =
        "https://www.google.com/maps/search/?api=1&query=" +
            String.format(Locale.US, "%.6f,%.6f", lat, lng)

    /** Sohbet uygulamaları bağlantıyı "|" karakterinde kesmesin diye parametreler kodlanır. */
    private fun routeUrl(trip: TripPlanResult): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        fun coord(lat: Double, lng: Double) = String.format(Locale.US, "%.6f,%.6f", lat, lng)
        val base = "https://www.google.com/maps/dir/?api=1" +
            "&origin=" + enc(coord(trip.origin.latitude, trip.origin.longitude)) +
            "&destination=" + enc(coord(trip.destination.latitude, trip.destination.longitude)) +
            "&travelmode=driving"
        if (!trip.hasStops) return base
        return base + "&waypoints=" +
            enc(trip.stops.joinToString("|") { coord(it.station.latitude, it.station.longitude) })
    }
}
