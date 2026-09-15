package com.yakitrotam.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.yakitrotam.app.data.model.FuelStop
import com.yakitrotam.app.data.model.TripPlanResult

object GoogleMapsLauncher {

    /**
     * Tüm hesaplanan yakıt duraklarını (waypoints) içeren eksiksiz rotayı
     * doğrudan resmi Google Maps uygulamasında veya tarayıcıda açar.
     */
    fun launchFullRouteInGoogleMaps(context: Context, tripResult: TripPlanResult) {
        val originStr = "${tripResult.origin.latitude},${tripResult.origin.longitude}"
        val destStr = "${tripResult.destination.latitude},${tripResult.destination.longitude}"

        val waypointsStr = if (tripResult.stops.isNotEmpty()) {
            tripResult.stops.joinToString("|") { stop ->
                "${stop.station.latitude},${stop.station.longitude}"
            }
        } else {
            null
        }

        val urlBuilder = StringBuilder("https://www.google.com/maps/dir/?api=1")
            .append("&origin=").append(Uri.encode(originStr))
            .append("&destination=").append(Uri.encode(destStr))
            .append("&travelmode=driving")

        if (!waypointsStr.isNullOrEmpty()) {
            urlBuilder.append("&waypoints=").append(Uri.encode(waypointsStr))
        }

        val mapsUri = Uri.parse(urlBuilder.toString())

        // Doğrudan Google Maps uygulamasını hedefle
        val mapsIntent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(mapsIntent)
        } catch (_: Exception) {
            // Google Maps yüklü değilse genel tarayıcı/harita seçici ile aç
            val fallbackIntent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Harita uygulaması açılamadı: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Kullanıcı belirli bir tek durağa (istasyona) doğrudan turn-by-turn navigasyon başlatmak isterse.
     */
    fun launchTurnByTurnToStation(context: Context, stop: FuelStop) {
        val gmmIntentUri = Uri.parse("google.navigation:q=${stop.station.latitude},${stop.station.longitude}&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            // Fallback web url
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${stop.station.latitude},${stop.station.longitude}")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
