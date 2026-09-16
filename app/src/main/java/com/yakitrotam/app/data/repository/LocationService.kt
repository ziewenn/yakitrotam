package com.yakitrotam.app.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
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
import kotlinx.coroutines.withTimeoutOrNull
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

/** Konum alınamadığında kullanıcıya doğrudan gösterilebilecek mesajı taşır. */
class LocationUnavailableException(message: String) : Exception(message)

class LocationService(
    private val routeRepository: RouteRepository = RouteRepository(),
    @Suppress("unused") private val applicationContext: Context? = null
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Cihaz konumunu döndürür. Alınamazsa kullanıcıya gösterilebilecek bir
     * mesajla [LocationUnavailableException] fırlatır.
     *
     * Önce Google Play Hizmetleri (FusedLocationProvider) denenir. Play Hizmetleri
     * olmayan (ör. Huawei) veya bu servisi hatalı dönen telefonlarda Android'in
     * kendi LocationManager'ına düşülür; eskiden bu durumda doğrudan hata veriliyordu.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): LatLng {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            throw LocationUnavailableException(
                "Konum izni verilmedi. Ayarlar > Uygulamalar > YakıtRotam > İzinler'den konuma izin verin."
            )
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null || !LocationManagerCompat.isLocationEnabled(locationManager)) {
            throw LocationUnavailableException(
                "Telefonun konum servisi kapalı. Bildirim panelinden konumu açıp tekrar deneyin."
            )
        }

        return withTimeoutOrNull(FUSED_TIMEOUT_MS) { fusedLocation(context) }
            ?: withTimeoutOrNull(PLATFORM_TIMEOUT_MS) { platformLocation(context, locationManager) }
            ?: lastKnownLocation(locationManager)
            ?: throw LocationUnavailableException(
                "Konum bulunamadı. Açık bir alanda birkaç saniye bekleyip tekrar deneyin."
            )
    }

    @SuppressLint("MissingPermission")
    private suspend fun fusedLocation(context: Context): LatLng? = runCatching {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        suspendCancellableCoroutine<LatLng?> { continuation ->
            val cancellation = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellation.cancel() }

            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(LatLng(location.latitude, location.longitude))
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last ->
                                continuation.resume(last?.let { LatLng(it.latitude, it.longitude) })
                            }
                            .addOnFailureListener { continuation.resume(null) }
                    }
                }
                .addOnFailureListener { continuation.resume(null) }
        }
    }.getOrNull()

    /**
     * Play Hizmetleri olmadan, sırasıyla ağ ve GPS sağlayıcılarından tek seferlik konum.
     *
     * GPS yalnızca "yaklaşık konum" izni verilmişken de denenir: Android bu durumda
     * sonucu bulanıklaştırarak döndürür. Eskiden bu izinde GPS atlanıyordu ve ağ
     * konumu kapalı telefonlarda konum hiç alınamıyordu.
     */
    @SuppressLint("MissingPermission")
    private suspend fun platformLocation(
        context: Context,
        locationManager: LocationManager
    ): LatLng? {
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        ).filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }

        for (provider in providers) {
            val location = runCatching {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(
                        locationManager,
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context),
                    ) { continuation.resume(it) }
                }
            }.getOrNull()
            if (location != null) return LatLng(location.latitude, location.longitude)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(locationManager: LocationManager): LatLng? =
        runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { LatLng(it.latitude, it.longitude) }

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
        const val FUSED_TIMEOUT_MS = 10_000L
        const val PLATFORM_TIMEOUT_MS = 12_000L
        const val USER_AGENT = "YakitRotamAndroidApp/1.0 (contact: github.com/yakitrotam)"
    }
}
