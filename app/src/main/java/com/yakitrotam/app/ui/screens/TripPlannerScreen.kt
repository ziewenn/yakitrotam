package com.yakitrotam.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.PlaceSuggestion
import com.yakitrotam.app.data.model.VehicleProfile
import com.yakitrotam.app.ui.components.BrandFilterBar
import com.yakitrotam.app.ui.components.FuelGaugeCard
import com.yakitrotam.app.ui.components.PlaceSearchDialog
import com.yakitrotam.app.ui.theme.*
import com.yakitrotam.app.ui.viewmodel.TripUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPlannerScreen(
    uiState: TripUiState,
    onSelectOrigin: (CityLocation) -> Unit,
    onSelectDestination: (CityLocation) -> Unit,
    onSwapLocations: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSearchPlaces: suspend (String) -> List<PlaceSuggestion>,
    onResolvePlace: suspend (PlaceSuggestion) -> CityLocation?,
    onUpdateProfile: (VehicleProfile) -> Unit,
    onToggleBrand: (FuelBrand) -> Unit,
    onSelectAllBrands: () -> Unit,
    onCalculateTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOriginSearch by remember { mutableStateOf(false) }
    var showDestinationSearch by remember { mutableStateOf(false) }


    // Konum izni isteği başlatıcı
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onUseCurrentLocation()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Uygulamanın amacını tek bakışta anlatan sürüş paneli
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(PrimaryBlueVariant, DarkSurfaceVariant, DarkSurface)
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    color = TextPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "AKILLI YAKIT PLANLAYICI",
                        color = PrimaryBlueSoft,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(TextPrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DirectionsCar, null, tint = TextPrimary, modifier = Modifier.size(27.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "YakıtRotam",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Daha az sapma, doğru zamanda yakıt molası",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Kalkış & Varış Noktası Seçim Kartı (Google Maps Arama Stili)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rotanı oluştur",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    // Hızlı GPS Butonu
                    TextButton(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        enabled = !uiState.isLocationLoading,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        if (uiState.isLocationLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = PrimaryBlue,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (uiState.isLocationLoading) "Konum Alınıyor..." else "Konumumu Kullan",
                            color = PrimaryBlue,
                            fontSize = 12.sp
                        )
                    }
                }

                // Kalkış Noktası Arama Alanı
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { showOriginSearch = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(SafeGreen)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NEREDEN",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Text(
                            text = uiState.origin.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Ara",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Değiştir (Swap) Butonu
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                    IconButton(
                        onClick = onSwapLocations,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Değiştir",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Varış Noktası Arama Alanı
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { showDestinationSearch = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(ReserveRed)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NEREYE",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Text(
                            text = uiState.destination.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Ara",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Araç Depo Hacmi, Kalan Yüzde ve 100km Tüketim Giriş Kartı
        FuelGaugeCard(
            vehicleProfile = uiState.vehicleProfile,
            onUpdateProfile = onUpdateProfile
        )

        // Tercih Edilen Akaryakıt Markaları Seçimi (Shell, Opet, PO vb.)
        BrandFilterBar(
            selectedBrands = uiState.selectedBrands,
            onToggleBrand = onToggleBrand,
            onSelectAll = onSelectAllBrands
        )

        // Hata Mesajı varsa göster
        if (uiState.errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ReserveRed.copy(alpha = 0.15f)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ReserveRed))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ReserveRed)
                    Text(text = uiState.errorMessage, color = TextPrimary, fontSize = 13.sp)
                }
            }
        }

        // Ana Hesaplama Butonu
        Button(
            onClick = onCalculateTrip,
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryBlue,
                contentColor = TextPrimary
            )
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = TextPrimary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Optimum Benzinlik Durakları Hesaplanıyor...", fontSize = 15.sp)
            } else {
                Icon(imageVector = Icons.Default.Route, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "En uygun rotayı planla",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Google Maps Tarzı Canlı Arama Modalleri
    if (showOriginSearch) {
        PlaceSearchDialog(
            title = "Kalkış Noktası Ara",
            isOriginSearch = true,
            onSelectPlace = {
                onSelectOrigin(it)
            },
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
            title = "Varış Noktası Ara",
            isOriginSearch = false,
            onSelectPlace = {
                onSelectDestination(it)
            },
            onSearchQueryChanged = onSearchPlaces,
            onResolvePlace = onResolvePlace,
            onDismiss = { showDestinationSearch = false }
        )
    }
}
