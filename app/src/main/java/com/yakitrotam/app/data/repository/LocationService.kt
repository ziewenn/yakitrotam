package com.yakitrotam.app.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.yakitrotam.app.BuildConfig
import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.data.model.PlaceSource
import com.yakitrotam.app.data.model.PlaceSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationService(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val applicationContext: Context? = null
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private var autocompleteToken = AutocompleteSessionToken.newInstance()

    private val placesClient: PlacesClient? by lazy {
        val context = applicationContext ?: return@lazy null
        val apiKey = BuildConfig.MAPS_API_KEY.trim()
        if (apiKey.isBlank()) return@lazy null

        if (!Places.isInitialized()) {
            Places.initialize(context, apiKey)
        }
        Places.createClient(context)
    }

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
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "YakitRotamAndroidApp/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string()?.let(::JSONObject)
                    val address = json?.optJSONObject("address")
                    if (address != null) {
                        val district = address.optString(
                            "suburb",
                            address.optString("district", address.optString("town", ""))
                        )
                        val province = address.optString(
                            "province",
                            address.optString("city", address.optString("state", ""))
                        )
                        val parts = listOf(district, province).filter(String::isNotBlank)
                        if (parts.isNotEmpty()) {
                            return@withContext "Konumum (${parts.joinToString(", ")})"
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Koordinatlar yine de kullanılabildiği için okunabilir bir yedek etiket döndürülür.
        }
        "Mevcut Konumum (%.4f, %.4f)".format(lat, lng)
    }

    /** Google Places tahminlerini döndürür; anahtar yoksa OSM ve yerel listeye geri düşer. */
    suspend fun searchPlaces(query: String): List<PlaceSuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return localSuggestions(trimmed)

        searchGooglePlaces(trimmed).takeIf { it.isNotEmpty() }?.let { return it }
        return searchOpenStreetMap(trimmed).ifEmpty { localSuggestions(trimmed) }
    }

    suspend fun resolvePlace(suggestion: PlaceSuggestion): CityLocation? {
        val lat = suggestion.latitude
        val lng = suggestion.longitude
        if (lat != null && lng != null) {
            return CityLocation(suggestion.title, suggestion.subtitle, lat, lng)
        }

        val client = placesClient ?: return null
        return try {
            val request = FetchPlaceRequest.newInstance(
                suggestion.id,
                listOf(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
            )
            val place = client.fetchPlace(request).awaitResult().place
            val location = place.latLng ?: return null
            autocompleteToken = AutocompleteSessionToken.newInstance()
            CityLocation(
                name = place.name ?: suggestion.title,
                province = place.address ?: suggestion.subtitle,
                latitude = location.latitude,
                longitude = location.longitude
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun searchGooglePlaces(query: String): List<PlaceSuggestion> {
        val client = placesClient ?: return emptyList()
        return try {
            val request = FindAutocompletePredictionsRequest.builder()
                .setCountries(listOf("TR"))
                .setSessionToken(autocompleteToken)
                .setQuery(query)
                .build()

            client.findAutocompletePredictions(request).awaitResult()
                .autocompletePredictions
                .take(10)
                .map { prediction ->
                    PlaceSuggestion(
                        id = prediction.placeId,
                        title = prediction.getPrimaryText(null).toString(),
                        subtitle = prediction.getSecondaryText(null).toString(),
                        source = PlaceSource.GOOGLE
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchOpenStreetMap(query: String): List<PlaceSuggestion> =
        withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&countrycodes=tr&format=json&addressdetails=1&limit=10&accept-language=tr"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "YakitRotamAndroidApp/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext emptyList()

                    val array = JSONArray(body)
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.getJSONObject(index)
                            val lat = item.optString("lat").toDoubleOrNull() ?: continue
                            val lng = item.optString("lon").toDoubleOrNull() ?: continue
                            val address = item.optJSONObject("address")
                            val displayName = item.optString("display_name")
                            val title = buildShortDisplayName(displayName, address)
                            val subtitle = address?.optString(
                                "province",
                                address.optString("city", address.optString("state", "Türkiye"))
                            ).orEmpty().ifBlank { "Türkiye" }
                            add(
                                PlaceSuggestion(
                                    id = "osm-${item.optString("place_id")}",
                                    title = title,
                                    subtitle = subtitle,
                                    source = PlaceSource.OPEN_STREET_MAP,
                                    latitude = lat,
                                    longitude = lng
                                )
                            )
                        }
                    }
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

    private fun buildShortDisplayName(fullDisplayName: String, address: JSONObject?): String {
        if (address == null) return fullDisplayName.split(",").take(2).joinToString(", ").trim()

        val main = address.optString(
            "amenity",
            address.optString(
                "tourism",
                address.optString(
                    "shop",
                    address.optString(
                        "road",
                        address.optString(
                            "suburb",
                            address.optString("district", address.optString("town", address.optString("city", "")))
                        )
                    )
                )
            )
        )
        return main.ifBlank { fullDisplayName.split(",").take(2).joinToString(", ").trim() }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
}
