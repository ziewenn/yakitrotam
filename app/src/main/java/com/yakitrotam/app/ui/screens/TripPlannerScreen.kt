package com.yakitrotam.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.ads.AdBanner
import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.PlaceSuggestion
import com.yakitrotam.app.data.model.VehicleProfile
import com.yakitrotam.app.ui.components.BrandFilterBar
import com.yakitrotam.app.ui.components.FuelGaugeCard
import com.yakitrotam.app.ui.components.FuelPriceStrip
import com.yakitrotam.app.ui.components.PlaceSearchDialog
import com.yakitrotam.app.ui.components.SectionCard
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBackground
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurface
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.HeroGradientEnd
import com.yakitrotam.app.ui.theme.HeroGradientMid
import com.yakitrotam.app.ui.theme.HeroGradientStart
import com.yakitrotam.app.ui.theme.ReserveRed
import com.yakitrotam.app.ui.theme.SafeGreen
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import com.yakitrotam.app.ui.viewmodel.TripUiState

@Composable
fun TripPlannerScreen(
    uiState: TripUiState,
    onSelectOrigin: (CityLocation) -> Unit,
    onSelectDestination: (CityLocation) -> Unit,
    onSwapLocations: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onLocationPermissionDenied: () -> Unit,
    onSearchPlaces: suspend (String) -> List<PlaceSuggestion>,
    onResolvePlace: suspend (PlaceSuggestion) -> CityLocation?,
    onUpdateProfile: (VehicleProfile) -> Unit,
    onToggleBrand: (FuelBrand) -> Unit,
    onSelectAllBrands: () -> Unit,
    onCalculateTrip: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOriginSearch by remember { mutableStateOf(false) }
    var showDestinationSearch by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) onUseCurrentLocation() else onLocationPermissionDenied()
    }

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlannerHero()

            FuelPriceStrip(
                snapshot = uiState.livePrice,
                selectedFuelType = uiState.vehicleProfile.fuelType
            )

            RouteSelectionCard(
                uiState = uiState,
                onOriginClick = { showOriginSearch = true },
                onDestinationClick = { showDestinationSearch = true },
                onSwapLocations = onSwapLocations,
                onRequestLocation = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )

            FuelGaugeCard(
                vehicleProfile = uiState.vehicleProfile,
                onUpdateProfile = onUpdateProfile
            )

            BrandFilterBar(
                selectedBrands = uiState.selectedBrands,
                onToggleBrand = onToggleBrand,
                onSelectAll = onSelectAllBrands
            )

            // Sabit alt panel (buton + banner reklam) içeriğin son kartını örtmesin.
            Spacer(Modifier.height(if (uiState.errorMessage != null) 250.dp else 170.dp))
        }

        // Ana eylem her zaman parmağın altında kalsın diye ekrana sabit.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, DarkBackground, DarkBackground)
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Hata mesajı butonun hemen üstünde durur; sayfanın sonunda kalınca
            // kullanıcı göremiyor ve "hiçbir şey olmadı" sanıyordu.
            uiState.errorMessage?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2A1416))
                        .border(1.dp, ReserveRed.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = ReserveRed, modifier = Modifier.size(20.dp))
                    Text(
                        message,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissError, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Kapat", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Button(
                onClick = onCalculateTrip,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentLime,
                    contentColor = Color.Black,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = TextPrimary
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = AccentLime,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = uiState.loadingStep.ifBlank { "Hesaplanıyor..." },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Icon(Icons.Default.Route, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Yakıt planımı oluştur", style = MaterialTheme.typography.titleLarge)
                }
            }

            AdBanner()
        }
    }

    if (showOriginSearch) {
        PlaceSearchDialog(
            title = "Kalkış noktası",
            isOriginSearch = true,
            onSelectPlace = onSelectOrigin,
            onUseCurrentLocation = {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onSearchQueryChanged = onSearchPlaces,
            onResolvePlace = onResolvePlace,
            onDismiss = { showOriginSearch = false }
        )
    }

    if (showDestinationSearch) {
        PlaceSearchDialog(
            title = "Varış noktası",
            isOriginSearch = false,
            onSelectPlace = onSelectDestination,
            onSearchQueryChanged = onSearchPlaces,
            onResolvePlace = onResolvePlace,
            onDismiss = { showDestinationSearch = false }
        )
    }
}

@Composable
private fun PlannerHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(listOf(HeroGradientStart, HeroGradientMid, HeroGradientEnd))
            )
            .border(1.dp, DarkBorder, RoundedCornerShape(26.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentLime),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocalGasStation,
                        null,
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        "YakıtRotam",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                    Text(
                        "Gerçek istasyonlar, güncel fiyatlar",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }
            Text(
                "Rotanı gir, deponu söyle; nerede ve ne kadar yakıt alman gerektiğini hesaplayalım.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun RouteSelectionCard(
    uiState: TripUiState,
    onOriginClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onSwapLocations: () -> Unit,
    onRequestLocation: () -> Unit
) {
    SectionCard(
        title = "Güzergah",
        icon = Icons.Default.Route,
        trailing = {
            TextButton(onClick = onRequestLocation, enabled = !uiState.isLocationLoading) {
                if (uiState.isLocationLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = AccentLime,
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Icon(Icons.Default.MyLocation, null, tint = AccentLime, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (uiState.isLocationLoading) "ALINIYOR" else "KONUMUM",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentLime
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LocationRow(
                    label = "Nereden",
                    value = uiState.origin.name,
                    dotColor = SafeGreen,
                    onClick = onOriginClick
                )
                LocationRow(
                    label = "Nereye",
                    value = uiState.destination.name,
                    dotColor = ReserveRed,
                    onClick = onDestinationClick
                )
            }
            IconButton(
                onClick = onSwapLocations,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, CircleShape)
            ) {
                Icon(Icons.Default.SwapVert, "Yön değiştir", tint = AccentLime, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun LocationRow(
    label: String,
    value: String,
    dotColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(DarkSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        Column(modifier = Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Sağdaki takas butonuna yer aç
        Spacer(Modifier.width(34.dp))
    }
}
