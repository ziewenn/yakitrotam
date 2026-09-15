package com.yakitrotam.app.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.data.model.PlaceSource
import com.yakitrotam.app.data.model.PlaceSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Adres arama ve ters coğrafi kodlama.
 *
 * Google Places ücretli olduğu için kullanılmıyor; yazarken arama (autocomplete)
 * Photon (OpenStreetMap tabanlı, anahtarsız), ters kodlama Nominatim üzerinden yapılır.
 * Cihaz konumu için kullanılan FusedLocationProvider ücretsizdir.
 */
class LocationService(
    private val routeRepository: RouteRepository = RouteRepository(),
    @Suppress("unused") private val applicationContext: Context? = null
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): LatLng? = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return@withContext null

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellation.cancel() }

            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(LatLng(location.latitude, location.longitude))
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { lastLocation ->
                                continuation.resume(
                                    lastLocation?.let { LatLng(it.latitude, it.longitude) }
                                )
                            }
                            .addOnFailureListener { continuation.resume(null) }
                    }
                }
                .addOnFailureListener { continuation.resume(null) }
        }
    }

    suspend fun reverseGeocode(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json&accept-language=tr"
            val json = getJsonObject(url)
            val address = json?.optJSONObject("address")
            if (address != null) {
                val district = address.firstNonBlank("suburb", "district", "town", "city_district")
                val province = address.firstNonBlank("province", "city", "state")
                val parts = listOf(district, province).filter(String::isNotBlank).distinct()
                if (parts.isNotEmpty()) {
                    return@withContext "Konumum (${parts.joinToString(", ")})"
                }
            }
        } catch (_: Exception) {
            // Koordinatlar yine de kullanılabildiği için okunabilir bir yedek etiket döndürülür.
        }
        "Mevcut Konumum (%.4f, %.4f)".format(Locale.US, lat, lng)
    }

    /** Photon üzerinden canlı yer arama; sonuç yoksa yerel popüler şehir listesine düşer. */
    suspend fun searchPlaces(query: String): List<PlaceSuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return localSuggestions(trimmed)
        return searchPhoton(trimmed).ifEmpty { localSuggestions(trimmed) }
    }

    suspend fun resolvePlace(suggestion: PlaceSuggestion): CityLocation? {
        val lat = suggestion.latitude ?: return null
        val lng = suggestion.longitude ?: return null
        return CityLocation(suggestion.title, suggestion.subtitle, lat, lng)
    }

    private suspend fun searchPhoton(query: String): List<PlaceSuggestion> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                // Türkiye merkezine yakın sonuçlar öne alınır (bias), sınırlama değil.
                val url = "https://photon.komoot.io/api/?q=$encoded&limit=12&lang=default&lat=39.0&lon=35.0"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext emptyList()

                    val features = JSONObject(body).optJSONArray("features")
                        ?: return@withContext emptyList()

                    buildList {
                        for (index in 0 until features.length()) {
                            val feature = features.optJSONObject(index) ?: continue
                            val properties = feature.optJSONObject("properties") ?: continue
                            if (!properties.optString("countrycode").equals("TR", ignoreCase = true)) continue

                            val coordinates = feature.optJSONObject("geometry")
                                ?.optJSONArray("coordinates") ?: continue
                            val lng = coordinates.optDouble(0)
                            val lat = coordinates.optDouble(1)
                            if (lat.isNaN() || lng.isNaN()) continue

                            val title = properties.firstNonBlank("name", "street", "city", "state")
                            if (title.isBlank()) continue
                            val subtitle = listOf(
                                properties.firstNonBlank("district", "city"),
                                properties.firstNonBlank("state", "county")
                            ).filter(String::isNotBlank).distinct().joinToString(", ")

                            add(
                                PlaceSuggestion(
                                    id = "photon-${properties.optString("osm_type")}-${properties.optLong("osm_id")}-$index",
                                    title = title,
                                    subtitle = subtitle.ifBlank { "Türkiye" },
                                    source = PlaceSource.OPEN_STREET_MAP,
                                    latitude = lat,
                                    longitude = lng
                                )
                            )
                        }
                    }.distinctBy { "%.4f,%.4f".format(Locale.US, it.latitude, it.longitude) }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun localSuggestions(query: String): List<PlaceSuggestion> =
        routeRepository.popularCities
            .filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                    it.province.contains(query, ignoreCase = true)
            }
            .mapIndexed { index, city ->
                PlaceSuggestion(
                    id = "local-$index-${city.name}",
                    title = city.name,
                    subtitle = city.province,
                    source = PlaceSource.LOCAL,
                    latitude = city.latitude,
                    longitude = city.longitude
                )
            }

    private fun getJsonObject(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            return if (body.isBlank()) null else JSONObject(body)
        }
    }

    private fun JSONObject.firstNonBlank(vararg keys: String): String {
        for (key in keys) {
            val value = optString(key)
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private companion object {
        const val USER_AGENT = "YakitRotamAndroidApp/1.0 (contact: github.com/yakitrotam)"
    }
}
