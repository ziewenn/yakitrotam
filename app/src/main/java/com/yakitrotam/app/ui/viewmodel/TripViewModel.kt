package com.yakitrotam.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yakitrotam.app.data.model.*
import com.yakitrotam.app.data.repository.FuelPriceRepository
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.data.repository.LocationService
import com.yakitrotam.app.data.repository.LocationUnavailableException
import com.yakitrotam.app.data.repository.RouteRepository
import com.yakitrotam.app.data.repository.SavedTripState
import com.yakitrotam.app.data.repository.TripPreferences
import com.yakitrotam.app.domain.FuelOptimizerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val errorMessage: String? = null,
    val isFirstRun: Boolean = false
)

@OptIn(FlowPreview::class)
class TripViewModel(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val stationRepository: GasStationRepository = GasStationRepository(),
    private val optimizerEngine: FuelOptimizerEngine = FuelOptimizerEngine(stationRepository),
    private val locationService: LocationService = LocationService(routeRepository),
    private val fuelPriceRepository: FuelPriceRepository = FuelPriceRepository(),
    private val preferences: TripPreferences? = null
) : ViewModel() {

    val popularCities: List<CityLocation> = routeRepository.popularCities

    /** Son oturumdan kalan girdiler; araç bilgileri her açılışta yeniden sorulmasın diye. */
    private var saved: SavedTripState = preferences?.load() ?: SavedTripState()

    private val _uiState = MutableStateFlow(
        TripUiState(
            origin = saved.origin
                ?: popularCities.firstOrNull { it.name.contains("İstanbul") } ?: popularCities[0],
            destination = saved.destination
                ?: popularCities.firstOrNull { it.name.contains("Antalya") } ?: popularCities[1],
            vehicleProfile = saved.vehicleProfile,
            selectedBrands = saved.selectedBrands,
            isFirstRun = preferences?.isFirstRun() == true
        )
    )
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    init {
        refreshPrices()

        if (preferences != null) {
            viewModelScope.launch {
                _uiState
                    .map { SavedInputs(it.vehicleProfile, it.selectedBrands, it.origin, it.destination) }
                    .distinctUntilChanged()
                    .drop(1) // açılışta yüklenen değeri geri yazmaya gerek yok
                    .debounce(400) // yakıt kadranı sürüklenirken her adımda diske yazılmasın
                    .collect { inputs ->
                        saved = saved.copy(
                            vehicleProfile = inputs.vehicleProfile,
                            selectedBrands = inputs.selectedBrands,
                            origin = inputs.origin,
                            destination = inputs.destination
                        )
                        preferences.save(saved)
                    }
            }
        }
    }

    private data class SavedInputs(
        val vehicleProfile: VehicleProfile,
        val selectedBrands: Set<FuelBrand>,
        val origin: CityLocation,
        val destination: CityLocation
    )

    /** Karşılama sayfası kapatıldı; değişiklik yapılmasa da bir daha gösterilmez. */
    fun completeFirstRun() {
        _uiState.update { it.copy(isFirstRun = false) }
        preferences?.save(saved)
    }

    /** Aramadan seçilen yeri "son aranan yerler" listesinin başına ekler. */
    private fun rememberPlace(place: CityLocation) {
        saved = saved.withRecentPlace(place)
        preferences?.save(saved)
    }

    /** Planlama ekranında güncel pompa fiyatını göstermek için arka planda çeker. */
    fun refreshPrices() {
        viewModelScope.launch {
            val snapshot = fuelPriceRepository.getPrices(_uiState.value.origin.province)
            _uiState.update { it.copy(livePrice = snapshot) }
        }
    }

    fun setOrigin(city: CityLocation) {
        rememberPlace(city)
        _uiState.update { it.copy(origin = city, tripResult = null, errorMessage = null) }
        refreshPrices()
    }

    fun setDestination(city: CityLocation) {
        rememberPlace(city)
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

    /** Arama kutusu boşken önce son aranan yerler, ardından popüler şehirler gösterilir. */
    suspend fun searchPlaces(query: String): List<PlaceSuggestion> {
        if (query.isNotBlank()) return locationService.searchPlaces(query)

        val recents = saved.recentPlaces.mapIndexed { index, place ->
            PlaceSuggestion(
                id = "recent-$index-${place.name}",
                title = place.name,
                subtitle = place.province,
                source = PlaceSource.RECENT,
                latitude = place.latitude,
                longitude = place.longitude
            )
        }
        val recentNames = recents.mapTo(HashSet()) { it.title }
        return recents + locationService.searchPlaces("").filterNot { it.title in recentNames }
    }

    fun onLocationPermissionDenied() {
        _uiState.update {
            it.copy(
                isLocationLoading = false,
                errorMessage = "Konum izni verilmedi. Konumunuzu kullanmak için izin vermeniz gerekiyor; " +
                    "kalkış noktasını aramadan da seçebilirsiniz."
            )
        }
    }

    suspend fun resolvePlace(suggestion: PlaceSuggestion): CityLocation? =
        locationService.resolvePlace(suggestion)

    /** Haritadan seçilen noktayı adrese çevirir. */
    suspend fun describeMapPoint(point: LatLng): CityLocation =
        locationService.describePoint(point.latitude, point.longitude)

    /**
     * GPS'ten anlık konumu alıp kalkış noktası yapar
     */
    fun useCurrentLocation(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocationLoading = true, errorMessage = null) }
            try {
                val coords = locationService.getCurrentLocation(context)
                val originLocation = locationService.reverseGeocode(coords.latitude, coords.longitude)
                _uiState.update {
                    it.copy(
                        origin = originLocation,
                        isLocationLoading = false,
                        tripResult = null
                    )
                }
                refreshPrices()
            } catch (e: LocationUnavailableException) {
                _uiState.update { it.copy(isLocationLoading = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLocationLoading = false,
                        errorMessage = "Konum alınamadı: ${e.localizedMessage ?: "bilinmeyen hata"}"
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

                forcedStops = emptyMap()
                // Motor, adayların gerçek yol sapması için ağ çağrısı yapar; IO iş parçacığında çalışır.
                val result = withContext(Dispatchers.IO) {
                    optimizerEngine.calculateTripPlan(
                        origin = currentState.origin,
                        destination = currentState.destination,
                        routePoints = routePoints,
                        vehicleProfile = currentState.vehicleProfile,
                        preferredBrands = currentState.selectedBrands,
                        fuelPrice = priceSnapshot,
                        detourResolver = detourResolver
                    )
                }

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

    private val detourResolver = DetourResolver(routeRepository::roadDetours)

    /** Kullanıcının alternatiflerden seçtiği duraklar; yeni rota hesaplanınca sıfırlanır. */
    private var forcedStops: Map<Int, String> = emptyMap()

    /**
     * [stopIndex]. durağı [stationId] istasyonuyla değiştirir ve planı yeniden hesaplar.
     * Önceki duraklar korunur; sonraki duraklar yakıt durumu değiştiği için yeniden seçilir.
     * İstasyon verisi zaten bellekte olduğundan ağa çıkılmaz.
     */
    fun selectAlternative(stopIndex: Int, stationId: String) {
        val trip = _uiState.value.tripResult ?: return
        forcedStops = trip.stops
            .filter { it.stopIndex < stopIndex }
            .associate { it.stopIndex to it.station.id } + (stopIndex to stationId)

        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                optimizerEngine.calculateTripPlan(
                    origin = trip.origin,
                    destination = trip.destination,
                    routePoints = trip.routePoints,
                    vehicleProfile = trip.vehicleProfile,
                    preferredBrands = trip.preferredBrands,
                    fuelPrice = trip.fuelPrice,
                    forcedStationIds = forcedStops,
                    detourResolver = detourResolver
                )
            }
            _uiState.update { it.copy(tripResult = updated) }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(tripResult = null) }
    }
}
