package com.yakitrotam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yakitrotam.app.data.repository.GasStationRepository
import com.yakitrotam.app.data.repository.LocationService
import com.yakitrotam.app.data.repository.FuelPriceRepository
import com.yakitrotam.app.data.repository.RouteRepository
import com.yakitrotam.app.data.repository.TripPreferences
import com.yakitrotam.app.domain.FuelOptimizerEngine
import com.yakitrotam.app.ui.screens.RouteSummaryScreen
import com.yakitrotam.app.ui.screens.TripPlannerScreen
import com.yakitrotam.app.ui.theme.DarkBackground
import com.yakitrotam.app.ui.theme.YakitRotamTheme
import com.yakitrotam.app.ui.viewmodel.TripViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TripViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val stationRepo = GasStationRepository()
                val routeRepo = RouteRepository()
                val optimizer = FuelOptimizerEngine(stationRepo)
                val locationService = LocationService(routeRepo, applicationContext)
                return TripViewModel(
                    routeRepository = routeRepo,
                    stationRepository = stationRepo,
                    optimizerEngine = optimizer,
                    locationService = locationService,
                    fuelPriceRepository = FuelPriceRepository(),
                    preferences = TripPreferences(applicationContext)
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YakitRotamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    if (uiState.tripResult != null) {
                        BackHandler {
                            viewModel.clearResult()
                        }
                        RouteSummaryScreen(
                            tripResult = uiState.tripResult!!,
                            onBackToPlanner = { viewModel.clearResult() }
                        )
                    } else {
                        TripPlannerScreen(
                            uiState = uiState,
                            onSelectOrigin = { viewModel.setOrigin(it) },
                            onSelectDestination = { viewModel.setDestination(it) },
                            onSwapLocations = { viewModel.swapOriginDestination() },
                            onUseCurrentLocation = { viewModel.useCurrentLocation(this@MainActivity) },
                            onLocationPermissionDenied = { viewModel.onLocationPermissionDenied() },
                            onSearchPlaces = { viewModel.searchPlaces(it) },
                            onResolvePlace = { viewModel.resolvePlace(it) },
                            onUpdateProfile = { viewModel.updateVehicleProfile(it) },
                            onToggleBrand = { viewModel.toggleBrand(it) },
                            onSelectAllBrands = { viewModel.selectAllBrands() },
                            onCalculateTrip = { viewModel.calculateRoute() },
                            onDismissError = { viewModel.dismissError() }
                        )
                    }
                }
            }
        }
    }
}
