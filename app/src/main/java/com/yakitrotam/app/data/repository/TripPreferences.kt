package com.yakitrotam.app.data.repository

import android.content.Context
import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.VehicleProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Uygulama kapanıp açıldığında geri yüklenen kullanıcı girdileri. */
@Serializable
data class SavedTripState(
    val vehicleProfile: VehicleProfile = VehicleProfile(),
    val selectedBrands: Set<FuelBrand> = emptySet(),
    val origin: CityLocation? = null,
    val destination: CityLocation? = null,
    val recentPlaces: List<CityLocation> = emptyList()
) {
    /** [place]'i en başa alır, aynı yeri tekrar eklemez, listeyi [MAX_RECENT_PLACES] ile sınırlar. */
    fun withRecentPlace(place: CityLocation): SavedTripState = copy(
        recentPlaces = (listOf(place) + recentPlaces.filterNot { it.isSamePlaceAs(place) })
            .take(MAX_RECENT_PLACES)
    )

    companion object {
        const val MAX_RECENT_PLACES = 8
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(state: SavedTripState): String = json.encodeToString(state)

        /** Bozuk veya eski sürümden kalma kayıtta varsayılanlara döner, uygulamayı çökertmez. */
        fun decode(raw: String?): SavedTripState =
            raw?.let { runCatching { json.decodeFromString<SavedTripState>(it) }.getOrNull() }
                ?: SavedTripState()
    }
}

/** ~100 m içindeki aynı isimli yerler tekrar sayılır. */
private fun CityLocation.isSamePlaceAs(other: CityLocation): Boolean =
    name == other.name &&
        kotlin.math.abs(latitude - other.latitude) < 0.001 &&
        kotlin.math.abs(longitude - other.longitude) < 0.001

/**
 * Son girilen araç profili, marka tercihi, kalkış/varış ve son aranan yerleri
 * SharedPreferences'ta tek bir JSON olarak saklar.
 */
class TripPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("trip_preferences", Context.MODE_PRIVATE)

    fun load(): SavedTripState = SavedTripState.decode(prefs.getString(KEY_STATE, null))

    fun save(state: SavedTripState) {
        prefs.edit().putString(KEY_STATE, SavedTripState.encode(state)).apply()
    }

    private companion object {
        const val KEY_STATE = "saved_trip_state_v1"
    }
}
