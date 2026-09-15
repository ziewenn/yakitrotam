package com.yakitrotam.app.data.repository

import android.content.Context
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.model.GasStation
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.util.GeoUtils
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

class GasStationRepository(
    private val context: Context? = null,
    initialStations: List<GasStation> = emptyList()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedStations: List<GasStation> = initialStations

    init {
        if (cachedStations.isEmpty() && context != null) {
            loadStationsFromAssets()
        }
    }

    fun loadStationsFromAssets(): List<GasStation> {
        if (context == null) return cachedStations
        try {
            val inputStream = context.assets.open("turkey_gas_stations.json")
            val reader = InputStreamReader(inputStream)
            val jsonString = reader.readText()
            reader.close()
            cachedStations = json.decodeFromString(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cachedStations
    }

    fun getAllStations(): List<GasStation> = cachedStations

    fun setStations(stations: List<GasStation>) {
        cachedStations = stations
    }

    /**
     * Belirli bir yakıt türü ve marka tercihine uyan istasyonları filtreler.
     * preferredBrands boş ise tüm markalar kabul edilir.
     */
    fun filterStations(
        fuelType: FuelType,
        preferredBrands: Set<FuelBrand> = emptySet(),
        requireTasitTanima: Boolean = false
    ): List<GasStation> {
        return cachedStations.filter { station ->
            val brandMatch = preferredBrands.isEmpty() || preferredBrands.contains(station.brand)
            val fuelMatch = station.supportsFuel(fuelType)
            val tasitTanimaMatch = !requireTasitTanima || station.hasTasitTanima
            brandMatch && fuelMatch && tasitTanimaMatch
        }
    }

    /**
     * Verilen bir referans koordinata en yakın ve rota koridorundan en az sapan aday istasyonları bulur.
     */
    fun findCandidateStationsAlongCorridor(
        searchCenter: LatLng,
        routeSegment: List<LatLng>,
        fuelType: FuelType,
        preferredBrands: Set<FuelBrand>,
        maxSearchRadiusKm: Double = 45.0,
        maxDetourFromRouteKm: Double = 8.0
    ): List<Pair<GasStation, Double>> {
        val eligible = filterStations(fuelType, preferredBrands)

        return eligible
            .mapNotNull { station ->
                val distToCenter = GeoUtils.distanceKm(searchCenter, station.location)
                if (distToCenter <= maxSearchRadiusKm) {
                    val detour = GeoUtils.minDistanceToRouteKm(station.location, routeSegment)
                    if (detour <= maxDetourFromRouteKm) {
                        // Ağırlıklı skor: hem merkeze yakınlık hem rota sapması
                        val penalty = detour * 1.5 + distToCenter * 0.5
                        Pair(station, penalty)
                    } else null
                } else null
            }
            .sortedBy { it.second }
    }
}
