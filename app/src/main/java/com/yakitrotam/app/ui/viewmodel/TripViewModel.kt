package com.yakitrotam.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yakitrotam.app.data.model.*
import com.yakitrotam.app.data.repository.FuelPriceRepository
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.data.repository.LocationService
import com.yakitrotam.app.data.repository.RouteRepository
import com.yakitrotam.app.domain.FuelOptimizerEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripUiState(
    val origin: CityLocation,
    val destination: CityLocation,
    val vehicleProfile: VehicleProfile = VehicleProfile(),
    val selectedBrands: Set<FuelBrand> = emptySet(), // Boş = Tümü kabul edilir
    val tripResult: TripPlanResult? = null,
    val livePrice: FuelPriceSnapshot? = null,
    val isLoading: Boolean = false,
    val loadingStep: String = "",
    val isLocationLoading: Boolean = false,
    val errorMessage: String? = null
)

class TripViewModel(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val stationRepository: GasStationRepository = GasStationRepository(),
    private val optimizerEngine: FuelOptimizerEngine = FuelOptimizerEngine(stationRepository),
    private val locationService: LocationService = LocationService(routeRepository),
    private val fuelPriceRepository: FuelPriceRepository = FuelPriceRepository()
) : ViewModel() {

    val popularCities: List<CityLocation> = routeRepository.popularCities

    private val _uiState = MutableStateFlow(
        TripUiState(
            origin = popularCities.firstOrNull { it.name.contains("İstanbul") } ?: popularCities[0],
            destination = popularCities.firstOrNull { it.name.contains("Antalya") } ?: popularCities[1],
            selectedBrands = emptySet() // Varsayılan: tüm markalar
        )
    )
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    init {
        refreshPrices()
    }

    /** Planlama ekranında güncel pompa fiyatını göstermek için arka planda çeker. */
    fun refreshPrices() {
        viewModelScope.launch {
            val snapshot = fuelPriceRepository.getPrices(_uiState.value.origin.province)
            _uiState.update { it.copy(livePrice = snapshot) }
        }
    }

    fun setOrigin(city: CityLocation) {
        _uiState.update { it.copy(origin = city, tripResult = null, errorMessage = null) }
        refreshPrices()
    }

    fun setDestination(city: CityLocation) {
        _uiState.update { it.copy(destination = city, tripResult = null, errorMessage = null) }
    }

    fun swapOriginDestination() {
        _uiState.update { current ->
            current.copy(
                origin = current.destination,
                destination = current.origin,
                tripResult = null,
                errorMessage = null
            )
        }
        refreshPrices()
    }

    fun updateVehicleProfile(profile: VehicleProfile) {
        _uiState.update { it.copy(vehicleProfile = profile) }
    }

    fun toggleBrand(brand: FuelBrand) {
        _uiState.update { current ->
            val updatedBrands = if (current.selectedBrands.contains(brand)) {
                current.selectedBrands - brand
            } else {
                current.selectedBrands + brand
            }
            current.copy(selectedBrands = updatedBrands)
        }
    }

    fun selectAllBrands() {
        _uiState.update { it.copy(selectedBrands = emptySet()) }
    }

    suspend fun searchPlaces(query: String): List<PlaceSuggestion> =
        locationService.searchPlaces(query)

    suspend fun resolvePlace(suggestion: PlaceSuggestion): CityLocation? =
        locationService.resolvePlace(suggestion)

    /**
     * GPS'ten anlık konumu alıp kalkış noktası yapar
     */
    fun useCurrentLocation(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocationLoading = true, errorMessage = null) }
            try {
                val coords = locationService.getCurrentLocation(context)
                if (coords != null) {
                    val placeName = locationService.reverseGeocode(coords.latitude, coords.longitude)
                    val originLocation = CityLocation(
                        name = placeName,
                        province = "Mevcut Konum",
                        latitude = coords.latitude,
                        longitude = coords.longitude
                    )
                    _uiState.update {
                        it.copy(
                            origin = originLocation,
                            isLocationLoading = false,
                            tripResult = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLocationLoading = false,
                            errorMessage = "Konum alınamadı. Lütfen cihaz konumunuzun açık ve iznin verildiğinden emin olun."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLocationLoading = false,
                        errorMessage = "Konum hatası: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun calculateRoute() {
        viewModelScope.launch {
            val currentState = _uiState.value
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, loadingStep = "Rota çiziliyor...")
            }

            try {
                val routePoints = routeRepository.getRoutePoints(
                    start = currentState.origin.latLng,
                    end = currentState.destination.latLng
                )

                if (routePoints.size < 2) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingStep = "",
                            errorMessage = "Rota oluşturulamadı. Lütfen kalkış ve varış noktalarını kontrol edin."
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(loadingStep = "Güzergahtaki istasyonlar ve fiyatlar alınıyor...") }

                // İstasyonlar ve fiyatlar birbirinden bağımsız; paralel çekilir.
                val (stations, priceSnapshot) = coroutineScope {
                    val stationsJob = async { stationRepository.loadStationsAlongRoute(routePoints) }
                    val priceJob = async { fuelPriceRepository.getPrices(currentState.origin.province) }
                    stationsJob.await() to priceJob.await()
                }

                if (stations.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingStep = "",
                            errorMessage = "İstasyon verisi alınamadı (OpenStreetMap'e ulaşılamadı). " +
                                "İnternet bağlantınızı kontrol edip tekrar deneyin."
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(loadingStep = "Duraklar hesaplanıyor...") }

                val result = optimizerEngine.calculateTripPlan(
                    origin = currentState.origin,
                    destination = currentState.destination,
                    routePoints = routePoints,
                    vehicleProfile = currentState.vehicleProfile,
                    preferredBrands = currentState.selectedBrands,
                    fuelPrice = priceSnapshot
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingStep = "",
                        tripResult = result,
                        livePrice = priceSnapshot,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingStep = "",
                        errorMessage = "Hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}"
                    )
                }
            }
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(tripResult = null) }
    }
}
