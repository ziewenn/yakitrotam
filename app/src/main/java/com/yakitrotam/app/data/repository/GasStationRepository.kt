package com.yakitrotam.app.data.repository

import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.model.GasStation
import com.yakitrotam.app.data.model.LatLng

/**
 * İstasyon kaynağı. Veriler artık uygulamaya gömülü statik listeden değil,
 * her rota için OpenStreetMap'ten canlı çekilir; böylece gerçekte var olmayan
 * konumlara durak konulmaz.
 */
class GasStationRepository(
    private val remoteSource: OverpassStationSource = OverpassStationSource(),
    initialStations: List<GasStation> = emptyList()
) {
    private var cachedStations: List<GasStation> = initialStations

    /**
     * Rota koridorundaki gerçek istasyonları indirir ve önbelleğe alır.
     * Ağ erişilemezse önceki önbellek korunur (boşsa boş liste döner).
     */
    suspend fun loadStationsAlongRoute(routePoints: List<LatLng>): List<GasStation> {
        val fetched = remoteSource.fetchAlongRoute(routePoints)
        if (fetched.isNotEmpty()) {
            cachedStations = fetched.distinctBy { it.id }
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
        preferredBrands: Set<FuelBrand> = emptySet()
    ): List<GasStation> = cachedStations.filter { station ->
        val brandMatch = preferredBrands.isEmpty() || preferredBrands.contains(station.brand)
        brandMatch && station.supportsFuel(fuelType)
    }
}
