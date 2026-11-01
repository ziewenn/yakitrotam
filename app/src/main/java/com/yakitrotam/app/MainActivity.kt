package com.yakitrotam.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import com.yakitrotam.app.util.GoogleMapsLauncher
import com.yakitrotam.app.ui.theme.CardSurface
import com.yakitrotam.app.ui.theme.AccentSoft
import com.yakitrotam.app.ui.theme.Accent
import com.yakitrotam.app.ui.screens.StationMapScreen
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yakitrotam.app.ads.Ads
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.data.repository.LocationService
import com.yakitrotam.app.data.repository.OverpassStationSource
import com.yakitrotam.app.data.repository.FuelPriceRepository
import com.yakitrotam.app.data.repository.RouteRepository
import com.yakitrotam.app.data.repository.Endpoints
import com.yakitrotam.app.data.repository.TripPreferences
import com.yakitrotam.app.domain.FuelOptimizerEngine
import com.yakitrotam.app.ui.screens.RouteSummaryScreen
import com.yakitrotam.app.ui.screens.TripPlannerScreen
import com.yakitrotam.app.ui.theme.AppBackground
import com.yakitrotam.app.ui.theme.YakitRotamTheme
import com.yakitrotam.app.ui.viewmodel.TripViewModel
import com.yakitrotam.app.util.ReviewPrompt
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: TripViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val stationRepo = GasStationRepository(
                    OverpassStationSource(datasetCache = File(cacheDir, "stations.json"))
                )
                val routeRepo = RouteRepository()
                val optimizer = FuelOptimizerEngine(stationRepo)
                val locationService = LocationService(routeRepo, applicationContext)
                return TripViewModel(
                    routeRepository = routeRepo,
                    stationRepository = stationRepo,
                    optimizerEngine = optimizer,
                    locationService = locationService,
                    fuelPriceRepository = FuelPriceRepository(),
                    preferences = TripPreferences(applicationContext),
                    onPlanReady = { ReviewPrompt.recordPlan(applicationContext) },
                    // Şarj istasyonlarının canlı yedeği yok: yalnızca EPDK'dan üretilen hazır liste.
                    evStationRepository = GasStationRepository(
                        OverpassStationSource(
                            fixedEndpoints = emptyList(),
                            datasetCache = File(cacheDir, "chargers.json"),
                            datasetUrl = { Endpoints.chargers }
                        )
                    )
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ hedeflenince uygulama kenardan kenara çizilir; açık tema için
        // sistem çubuk simgeleri koyu renkte tutulur.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, SYSTEM_BAR_DARK_SCRIM),
            // Android 8 öncesi koyu gezinme simgesi çizemez; o sürümlerde bu yarı saydam koyu zemin
            // kullanılır, yoksa beyaz simgeler açık zeminde kaybolur.
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, SYSTEM_BAR_DARK_SCRIM)
        )
        super.onCreate(savedInstanceState)
        Endpoints.init(this)
        Ads.initialize(this)
        // Planı Haritalar'da açıp uygulamaya geri dönmeyen kullanıcıya sonraki açılışta sorulur.
        if (savedInstanceState == null) ReviewPrompt.askIfDue(this)

        setContent {
            YakitRotamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppBackground
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    // Alt menü: 0 = rota planlayıcı, 1 = yakındaki istasyonlar haritası.
                    var tab by rememberSaveable { mutableIntStateOf(0) }

                    if (uiState.tripResult != null) {
                        // Plana bakıp geri dönen kullanıcı bir işin ortasında değildir; puan istemek için uygun an.
                        val closeSummary = {
                            viewModel.clearResult()
                            ReviewPrompt.askIfDue(this@MainActivity)
                        }
                        BackHandler(onBack = closeSummary)
                        RouteSummaryScreen(
                            tripResult = uiState.tripResult!!,
                            onBackToPlanner = closeSummary,
                            onSelectAlternative = { index, id -> viewModel.selectAlternative(index, id) }
                        )
                    } else Scaffold(
                        containerColor = AppBackground,
                        bottomBar = {
                            NavigationBar(containerColor = CardSurface) {
                                listOf(
                                    Triple("Rota", Icons.Default.Route, 0),
                                    Triple("Harita", Icons.Default.Map, 1)
                                ).forEach { (label, icon, index) ->
                                    NavigationBarItem(
                                        selected = tab == index,
                                        onClick = { tab = index },
                                        icon = { Icon(icon, contentDescription = null) },
                                        label = { Text(label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Accent,
                                            selectedTextColor = Accent,
                                            indicatorColor = AccentSoft
                                        )
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        // Ekranların kendi sistem çubuğu boşlukları alt menüyle iki kez sayılmasın.
                        val screenModifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                        BackHandler(enabled = tab != 0) { tab = 0 }
                        if (tab == 1) {
                            StationMapScreen(
                                startElectric = uiState.vehicleProfile.isElectric,
                                startCenter = uiState.origin.latLng,
                                loadStations = { viewModel.mapStations(it) },
                                locate = { viewModel.currentLocationOrNull(this@MainActivity) },
                                onNavigate = { GoogleMapsLauncher.launchTurnByTurn(this@MainActivity, it.latitude, it.longitude) },
                                onPlanTo = {
                                    viewModel.planToStation(it)
                                    tab = 0
                                },
                                modifier = screenModifier
                            )
                        } else TripPlannerScreen(
                            modifier = screenModifier,
                            uiState = uiState,
                            onSelectOrigin = { viewModel.setOrigin(it) },
                            onSelectDestination = { viewModel.setDestination(it) },
                            onSwapLocations = { viewModel.swapOriginDestination() },
                            onUseCurrentLocation = { viewModel.useCurrentLocation(this@MainActivity) },
                            onLocationPermissionDenied = { viewModel.onLocationPermissionDenied() },
                            onSearchPlaces = { viewModel.searchPlaces(it) },
                            onResolvePlace = { viewModel.resolvePlace(it) },
                            onDescribeMapPoint = { viewModel.describeMapPoint(it) },
                            onUpdateProfile = { viewModel.updateVehicleProfile(it) },
                            onToggleBrand = { viewModel.toggleBrand(it) },
                            onSelectAllBrands = { viewModel.selectAllBrands() },
                            onCalculateTrip = {
                                viewModel.calculateRoute()
                                // Hesap arkada sürerken tam ekran reklam; kapatılınca sonuç hazır olur.
                                Ads.showInterstitialIfDue(this@MainActivity)
                            },
                            onDismissError = { viewModel.dismissError() },
                            onFirstRunDone = { viewModel.completeFirstRun() }
                        )
                    }
                }
            }
        }
    }
}

/** androidx.activity'nin kendi varsayılan koyu zemini (#801B1B1B). */
private val SYSTEM_BAR_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
