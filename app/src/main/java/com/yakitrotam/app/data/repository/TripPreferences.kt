package com.yakitrotam.app.data.repository

import android.content.Context
import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.LogEntry
import com.yakitrotam.app.data.model.SavedRoute
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
    val recentPlaces: List<CityLocation> = emptyList(),
    /**
     * Diğer tür araç: yakıtlı araç seçiliyken kayıtlı elektrikli araç ya da tersi. Yakıt türü
     * elektrik ile yakıt arasında değişince profiller yer değiştirir; litre ve kWh karışmaz.
     */
    val otherVehicle: VehicleProfile? = null,
    val savedRoutes: List<SavedRoute> = emptyList(),
    /** Haritada öne çıkan istasyonlar ([com.yakitrotam.app.data.model.GasStation.id]). */
    val favoriteStationIds: Set<String> = emptySet(),
    /** Elektrikli araç: üyeliği olan şarj ağları; planda bu ağların istasyonları öne alınır. */
    val preferredOperators: Set<String> = emptySet(),
    val logEntries: List<LogEntry> = emptyList()
) {
    /** [place]'i en başa alır, aynı yeri tekrar eklemez, listeyi [MAX_RECENT_PLACES] ile sınırlar. */
    fun withRecentPlace(place: CityLocation): SavedTripState = copy(
        recentPlaces = (listOf(place) + recentPlaces.filterNot { it.isSamePlaceAs(place) })
            .take(MAX_RECENT_PLACES)
    )

    companion object {
        const val MAX_RECENT_PLACES = 8
        // coerceInputValues: eski bir sürüme dönülürse bilinmeyen enum değeri (ör. ELEKTRIK)
        // tüm kaydı silmek yerine varsayılana döner.
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

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

    /** Hiç kayıt yoksa uygulama ilk kez açılıyordur; araç bilgileri bir kez sorulur. */
    fun isFirstRun(): Boolean = !prefs.contains(KEY_STATE)

    fun load(): SavedTripState = SavedTripState.decode(prefs.getString(KEY_STATE, null))

    fun save(state: SavedTripState) {
        prefs.edit().putString(KEY_STATE, SavedTripState.encode(state)).apply()
    }

    private companion object {
        const val KEY_STATE = "saved_trip_state_v1"
    }
}
