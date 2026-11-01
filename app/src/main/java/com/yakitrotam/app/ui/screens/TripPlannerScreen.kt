package com.yakitrotam.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import com.yakitrotam.app.data.model.SavedRoute
import com.yakitrotam.app.ui.components.OperatorSheet
import com.yakitrotam.app.ui.theme.AccentSoft
import com.yakitrotam.app.ui.theme.CardSurface
import com.yakitrotam.app.ui.theme.FuelAmber
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yakitrotam.app.ads.AdBanner
import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.data.model.PlaceSuggestion
import com.yakitrotam.app.data.model.VehicleProfile
import com.yakitrotam.app.ui.components.BrandSheet
import com.yakitrotam.app.ui.components.FuelLevelCard
import com.yakitrotam.app.notify.PriceWatch
import com.yakitrotam.app.ui.components.BatteryCard
import com.yakitrotam.app.ui.components.FuelPriceStrip
import com.yakitrotam.app.ui.components.VehicleCard
import com.yakitrotam.app.ui.components.PlaceSearchDialog
import com.yakitrotam.app.ui.components.SettingRow
import com.yakitrotam.app.ui.components.SurfaceCard
import com.yakitrotam.app.ui.components.VehicleSheet
import com.yakitrotam.app.ui.components.formatDecimal
import com.yakitrotam.app.ui.theme.Accent
import com.yakitrotam.app.ui.theme.OnAccent
import com.yakitrotam.app.ui.theme.AppBackground
import com.yakitrotam.app.ui.theme.BorderColor
import com.yakitrotam.app.ui.theme.SurfaceMuted
import com.yakitrotam.app.ui.theme.ReserveRed
import com.yakitrotam.app.ui.theme.SafeGreen
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import com.yakitrotam.app.ui.viewmodel.TripUiState

private val LocationRowHeight = 64.dp

/**
 * Tek ekranlık planlayıcı. Her yolculukta değişen iki şey öndedir: güzergah ve depodaki
 * yakıt. Nadiren değişen araç bilgileri ve marka tercihi alttan açılan sayfalardadır.
 */
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
    onDescribeMapPoint: suspend (LatLng) -> CityLocation,
    onUpdateProfile: (VehicleProfile) -> Unit,
    onToggleBrand: (FuelBrand) -> Unit,
    onSelectAllBrands: () -> Unit,
    onCalculateTrip: () -> Unit,
    onDismissError: () -> Unit,
    onFirstRunDone: () -> Unit,
    onSaveRoute: () -> Unit = {},
    onApplySavedRoute: (SavedRoute) -> Unit = {},
    onRemoveSavedRoute: (SavedRoute) -> Unit = {},
    onToggleOperator: (String) -> Unit = {},
    onClearOperators: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showOriginSearch by remember { mutableStateOf(false) }
    var showDestinationSearch by remember { mutableStateOf(false) }
    var showVehicleSheet by remember { mutableStateOf(false) }
    var showBrandSheet by remember { mutableStateOf(false) }
    var showOperatorSheet by remember { mutableStateOf(false) }

    // İlk açılışta araç bilgileri bir kez sorulur; sonrasında kayıtlı değerler kullanılır.
    LaunchedEffect(uiState.isFirstRun) { if (uiState.isFirstRun) showVehicleSheet = true }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) onUseCurrentLocation() else onLocationPermissionDenied()
    }
    val context = LocalContext.current
    var priceAlertsEnabled by remember { mutableStateOf(PriceWatch.isEnabled(context)) }
    val setPriceAlerts = { enabled: Boolean ->
        PriceWatch.setEnabled(context, enabled)
        priceAlertsEnabled = enabled
        if (enabled) {
            val fuel = uiState.vehicleProfile.fuelType.shortName()
            Toast.makeText(context, "$fuel fiyatı değişince haber vereceğim", Toast.LENGTH_SHORT).show()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) setPriceAlerts(true)
        else Toast.makeText(context, "Bildirim izni olmadan haber veremem", Toast.LENGTH_SHORT).show()
    }
    val togglePriceAlerts = {
        val needsPermission = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
        when {
            priceAlertsEnabled -> setPriceAlerts(false)
            needsPermission -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> setPriceAlerts(true)
        }
    }

    val requestLocation = {
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    Box(modifier = modifier.fillMaxSize().background(AppBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "YakıtRotam",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp, bottom = 4.dp)
            )

            VehicleCard(
                vehicleProfile = uiState.vehicleProfile,
                onClick = { showVehicleSheet = true }
            )

            RouteCard(
                origin = uiState.origin.name,
                destination = uiState.destination.name,
                isLocationLoading = uiState.isLocationLoading,
                onOriginClick = { showOriginSearch = true },
                onDestinationClick = { showDestinationSearch = true },
                onSwapLocations = onSwapLocations,
                onRequestLocation = requestLocation
            )

            SavedRoutesRow(
                routes = uiState.savedRoutes,
                currentLabel = SavedRoute(uiState.origin, uiState.destination).label,
                onSave = onSaveRoute,
                onApply = onApplySavedRoute,
                onRemove = onRemoveSavedRoute
            )

            if (uiState.vehicleProfile.isElectric) {
                BatteryCard(vehicleProfile = uiState.vehicleProfile, onUpdate = onUpdateProfile)

                SurfaceCard(padding = 0.dp, spacing = 0.dp) {
                    SettingRow(
                        icon = Icons.Default.EvStation,
                        title = "Şarj ağı tercihi",
                        summary = if (uiState.preferredOperators.isEmpty()) "Tüm ağlar"
                        else uiState.preferredOperators.joinToString(", "),
                        onClick = { showOperatorSheet = true }
                    )
                }

                Text(
                    "Şarj istasyonları EPDK kayıtlarından alınır. Yalnızca DC hızlı şarj planlanır; " +
                        "fiyatlar operatörlerin üyeliksiz liste fiyatıdır.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else {
                FuelLevelCard(
                    vehicleProfile = uiState.vehicleProfile,
                    onLevelChange = { onUpdateProfile(uiState.vehicleProfile.copy(currentLevelPercent = it)) }
                )

                SurfaceCard(padding = 0.dp, spacing = 0.dp) {
                    SettingRow(
                        icon = Icons.Default.LocalGasStation,
                        title = "Marka tercihi",
                        summary = if (uiState.selectedBrands.isEmpty()) {
                            "Tüm markalar"
                        } else {
                            uiState.selectedBrands.joinToString(", ") { it.displayName }
                        },
                        onClick = { showBrandSheet = true }
                    )
                }

                FuelPriceStrip(
                    snapshot = uiState.livePrice,
                    selectedFuelType = uiState.vehicleProfile.fuelType,
                    priceAlertsEnabled = priceAlertsEnabled,
                    onTogglePriceAlerts = togglePriceAlerts,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Sabit alt panel (buton + banner reklam) son içeriği örtmesin.
            Spacer(Modifier.height(if (uiState.errorMessage != null) 250.dp else 170.dp))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, AppBackground, AppBackground)))
                .navigationBarsPadding()
                // Banner ile alt menü arasında boşluk: reklama yanlışlıkla dokunulmasın (AdMob yerleşim kuralı).
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.errorMessage?.let { ErrorBanner(it, onDismissError) }

            Button(
                onClick = onCalculateTrip,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent,
                    disabledContainerColor = SurfaceMuted,
                    disabledContentColor = TextPrimary
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        uiState.loadingStep.ifBlank { "Hesaplanıyor..." },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text("Durakları planla", style = MaterialTheme.typography.titleLarge)
                }
            }

            AdBanner()
        }
    }

    if (showOriginSearch) {
        PlaceSearchDialog(
            title = "Nereden",
            isOriginSearch = true,
            onSelectPlace = onSelectOrigin,
            onUseCurrentLocation = requestLocation,
            onSearchQueryChanged = onSearchPlaces,
            onResolvePlace = onResolvePlace,
            mapInitialCenter = uiState.origin.latLng,
            onDescribeMapPoint = onDescribeMapPoint,
            onDismiss = { showOriginSearch = false }
        )
    }

    if (showDestinationSearch) {
        PlaceSearchDialog(
            title = "Nereye",
            isOriginSearch = false,
            onSelectPlace = onSelectDestination,
            onSearchQueryChanged = onSearchPlaces,
            onResolvePlace = onResolvePlace,
            mapInitialCenter = uiState.destination.latLng,
            onDescribeMapPoint = onDescribeMapPoint,
            onDismiss = { showDestinationSearch = false }
        )
    }

    if (showVehicleSheet) {
        VehicleSheet(
            vehicleProfile = uiState.vehicleProfile,
            isFirstRun = uiState.isFirstRun,
            onUpdateProfile = onUpdateProfile,
            onDismiss = {
                showVehicleSheet = false
                if (uiState.isFirstRun) onFirstRunDone()
            }
        )
    }

    if (showOperatorSheet) {
        OperatorSheet(
            selected = uiState.preferredOperators,
            onToggle = onToggleOperator,
            onClear = onClearOperators,
            onDismiss = { showOperatorSheet = false }
        )
    }

    if (showBrandSheet) {
        BrandSheet(
            selectedBrands = uiState.selectedBrands,
            onToggleBrand = onToggleBrand,
            onSelectAll = onSelectAllBrands,
            onDismiss = { showBrandSheet = false }
        )
    }
}

/** Harita uygulamalarındaki yol tarifi girişine benzeyen iki satır: nereden, nereye. */
@Composable
private fun RouteCard(
    origin: String,
    destination: String,
    isLocationLoading: Boolean,
    onOriginClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onSwapLocations: () -> Unit,
    onRequestLocation: () -> Unit
) {
    SurfaceCard(padding = 0.dp, spacing = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Sol ray: kalkış noktası, kesikli çizgi, varış noktası.
            Canvas(modifier = Modifier.padding(start = 16.dp).size(width = 12.dp, height = LocationRowHeight * 2)) {
                val x = size.width / 2f
                val top = size.height * 0.25f
                val bottom = size.height * 0.75f
                drawLine(
                    color = TextMuted,
                    start = Offset(x, top + 10.dp.toPx()),
                    end = Offset(x, bottom - 10.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
                )
                drawCircle(SafeGreen, radius = 5.dp.toPx(), center = Offset(x, top))
                drawCircle(ReserveRed, radius = 5.dp.toPx(), center = Offset(x, bottom))
            }

            Column(modifier = Modifier.weight(1f)) {
                LocationRow(
                    label = "Nereden",
                    value = origin,
                    onClick = onOriginClick,
                    trailing = {
                        IconButton(onClick = onRequestLocation, enabled = !isLocationLoading) {
                            if (isLocationLoading) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.MyLocation, "Konumumu kullan", tint = Accent)
                            }
                        }
                    }
                )
                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(start = 14.dp))
                LocationRow(label = "Nereye", value = destination, onClick = onDestinationClick)
            }

            IconButton(
                onClick = onSwapLocations,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceMuted)
            ) {
                Icon(Icons.Default.SwapVert, "Kalkış ve varışı değiştir", tint = TextPrimary)
            }
        }
    }
}

@Composable
private fun LocationRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LocationRowHeight)
            .clickable(onClick = onClick)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke()
    }
}

/**
 * Kayıtlı rotalar: dokununca kalkış ve varış dolar, uzun basınca silinir. Şu anki rota kayıtlı
 * değilse sonda "Rotayı kaydet" çipi durur.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedRoutesRow(
    routes: List<SavedRoute>,
    currentLabel: String,
    onSave: () -> Unit,
    onApply: (SavedRoute) -> Unit,
    onRemove: (SavedRoute) -> Unit
) {
    val context = LocalContext.current
    val alreadySaved = routes.any { it.label == currentLabel }
    // Yeni kaydedilen rota başa eklenir; şerit kaymış kalmasın.
    val listState = rememberLazyListState()
    LaunchedEffect(routes.size) { listState.scrollToItem(0) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(routes, key = { it.label }) { route ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (route.label == currentLabel) AccentSoft else CardSurface)
                    .combinedClickable(
                        onClick = { onApply(route) },
                        onLongClick = {
                            onRemove(route)
                            Toast.makeText(context, "${route.label} silindi", Toast.LENGTH_SHORT).show()
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Star, null, tint = FuelAmber, modifier = Modifier.size(16.dp))
                Text(route.label, style = MaterialTheme.typography.labelLarge, color = TextPrimary, maxLines = 1)
            }
        }
        if (!alreadySaved) item {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        onSave()
                        Toast.makeText(context, "Kaydedildi. Silmek için çipe uzun bas.", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.StarBorder, null, tint = Accent, modifier = Modifier.size(16.dp))
                Text("Rotayı kaydet", style = MaterialTheme.typography.labelLarge, color = Accent)
            }
        }
    }
}

/** Hata mesajı ana butonun hemen üstünde durur; sayfanın sonunda kalınca fark edilmiyordu. */
@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFDECEC))
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = ReserveRed, modifier = Modifier.size(20.dp))
        Text(
            message,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, "Kapat", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
