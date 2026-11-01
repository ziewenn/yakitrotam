package com.yakitrotam.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yakitrotam.app.ads.AdBanner
import com.yakitrotam.app.data.model.GasStation
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.ui.components.BrandBadge
import com.yakitrotam.app.ui.components.CardShape
import com.yakitrotam.app.ui.components.MAP_ATTRIBUTION
import com.yakitrotam.app.ui.components.StationsMap
import com.yakitrotam.app.ui.components.formatDecimal
import com.yakitrotam.app.ui.components.formatPrice
import com.yakitrotam.app.ui.theme.Accent
import com.yakitrotam.app.ui.theme.AccentSoft
import com.yakitrotam.app.ui.theme.AppBackground
import com.yakitrotam.app.ui.theme.CardSurface
import com.yakitrotam.app.ui.theme.OnAccent
import com.yakitrotam.app.ui.theme.SurfaceMuted
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import com.yakitrotam.app.util.GeoUtils
import kotlinx.coroutines.launch

/** Haritanın etrafında listelenen en fazla istasyon ve uzaklığı. */
private const val NEARBY_COUNT = 15
private const val NEARBY_MAX_KM = 60.0
private const val FAST_CHARGE_KW = 150.0

/**
 * Yakındaki istasyonlar: akaryakıt ya da şarj istasyonları haritada, altında haritanın ortasına
 * en yakın olanlar. Bir istasyona dokununca yol tarifi ya da oraya rota planlama seçilir.
 * Açılışta araç elektrikliyse şarj istasyonları gösterilir.
 */
@Composable
fun StationMapScreen(
    startElectric: Boolean,
    startCenter: LatLng,
    loadStations: suspend (electric: Boolean) -> List<GasStation>?,
    locate: suspend () -> LatLng?,
    onNavigate: (GasStation) -> Unit,
    onPlanTo: (GasStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var electric by rememberSaveable { mutableStateOf(startElectric) }
    var fastOnly by rememberSaveable { mutableStateOf(false) }
    var all by remember { mutableStateOf<List<GasStation>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf(startCenter) }
    var focus by remember { mutableStateOf(startCenter) }
    var focusZoom by remember { mutableStateOf(11.0) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }

    LaunchedEffect(electric) {
        all = null
        failed = false
        selectedId = null
        val loaded = loadStations(electric)
        all = loaded
        failed = loaded == null
    }

    fun goToMyLocation() {
        scope.launch {
            locating = true
            locate()?.let {
                focus = it
                focusZoom = 12.0
            }
            locating = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.any { it }) goToMyLocation() }

    fun hasLocationPermission() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    // İzin önceden verilmişse harita doğrudan sürücünün çevresinde açılır.
    LaunchedEffect(Unit) { if (hasLocationPermission()) goToMyLocation() }

    val visible = remember(all, fastOnly, electric) {
        all.orEmpty().filter { !electric || !fastOnly || ((it.ccsKw ?: it.chademoKw ?: 0.0) >= FAST_CHARGE_KW) }
    }
    val nearby = remember(visible, center) {
        visible.asSequence()
            .map { it to GeoUtils.distanceKm(center, it.location) }
            .filter { it.second <= NEARBY_MAX_KM }
            .sortedBy { it.second }
            .take(NEARBY_COUNT)
            .toList()
    }
    val selected = remember(selectedId, visible) { visible.firstOrNull { it.id == selectedId } }

    Column(modifier = modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Text(
            "Yakındaki istasyonlar",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapChip("Akaryakıt", Icons.Default.LocalGasStation, selected = !electric) { electric = false }
            MapChip("Şarj (DC)", Icons.Default.Bolt, selected = electric) { electric = true }
            if (electric) MapChip("150+ kW", null, selected = fastOnly) { fastOnly = !fastOnly }
        }
        AdBanner(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            StationsMap(
                stations = visible,
                electric = electric,
                focus = focus,
                focusZoom = focusZoom,
                selectedId = selectedId,
                onCenterChanged = { center = it },
                onStationClick = { selectedId = it },
                modifier = Modifier.fillMaxSize()
            )

            Text(
                MAP_ATTRIBUTION,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(AppBackground.copy(alpha = 0.75f), RoundedCornerShape(bottomEnd = 8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )

            IconButton(
                onClick = { if (hasLocationPermission()) goToMyLocation() else permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                ) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(CardSurface)
            ) {
                if (locating) CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                else Icon(Icons.Default.MyLocation, "Konumuma git", tint = Accent)
            }

            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                when {
                    all == null && !failed -> StatusPill("İstasyonlar yükleniyor...")
                    failed -> StatusPill("İstasyon listesi alınamadı. İnternet bağlantını kontrol et.")
                    selected != null -> SelectedStationCard(
                        station = selected,
                        distanceKm = GeoUtils.distanceKm(center, selected.location),
                        onClose = { selectedId = null },
                        onNavigate = { onNavigate(selected) },
                        onPlanTo = { onPlanTo(selected) }
                    )
                    nearby.isEmpty() -> StatusPill("Bu çevrede istasyon yok; haritayı kaydır.")
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(nearby, key = { it.first.id }) { (station, km) ->
                            NearbyCard(station, km) {
                                selectedId = station.id
                                focus = station.location
                                focusZoom = 14.0
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(18.dp)) } },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CardSurface,
            labelColor = TextPrimary,
            iconColor = Accent,
            selectedContainerColor = Accent,
            selectedLabelColor = OnAccent,
            selectedLeadingIconColor = OnAccent
        ),
        border = null
    )
}

@Composable
private fun StatusPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextPrimary,
        modifier = Modifier
            .padding(16.dp)
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/** İstasyonun kısa bilgisi: şarjda güç ve fiyat, akaryakıtta marka ve yer. */
private fun stationInfo(station: GasStation): String =
    if (station.ccsKw != null || station.chademoKw != null) {
        listOfNotNull(
            station.chargeInfo?.substringAfter(" · ", station.chargeInfo)?.substringBefore(" · Yeşil")
                ?: "${formatDecimal(station.ccsKw ?: station.chademoKw ?: 0.0)} kW",
            station.kwhPriceTL?.let { "${formatPrice(it)}/kWh" }
        ).joinToString(" · ")
    } else {
        listOf(station.brand.displayName.takeIf { station.brand.name != "DIGER" }, station.city)
            .filterNot { it.isNullOrBlank() }.joinToString(" · ")
    }

private fun operatorOf(station: GasStation): String? =
    station.chargeInfo?.substringBefore(" · ")?.takeIf { it.isNotBlank() }

@Composable
private fun StationIcon(station: GasStation, size: Int) {
    if (station.ccsKw != null || station.chademoKw != null) {
        Box(Modifier.size(size.dp).clip(CircleShape).background(AccentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Bolt, null, tint = Accent, modifier = Modifier.size((size * 0.6f).dp))
        }
    } else {
        BrandBadge(brand = station.brand, size = size)
    }
}

@Composable
private fun NearbyCard(station: GasStation, distanceKm: Double, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(230.dp)
            .shadow(3.dp, CardShape)
            .clip(CardShape)
            .background(CardSurface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StationIcon(station, 32)
            Column(modifier = Modifier.weight(1f)) {
                Text(station.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatDecimal(distanceKm)} km", style = MaterialTheme.typography.labelMedium, color = Accent)
            }
        }
        Text(stationInfo(station), style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SelectedStationCard(
    station: GasStation,
    distanceKm: Double,
    onClose: () -> Unit,
    onNavigate: () -> Unit,
    onPlanTo: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth()
            .shadow(6.dp, CardShape)
            .clip(CardShape)
            .background(CardSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StationIcon(station, 40)
            Column(modifier = Modifier.weight(1f)) {
                Text(station.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(operatorOf(station), station.city.takeIf { it.isNotBlank() }, "${formatDecimal(distanceKm)} km")
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Kapat", tint = TextSecondary) }
        }
        val details = if (station.ccsKw != null || station.chademoKw != null) {
            listOfNotNull(
                station.chargeInfo?.substringAfter(" · ", ""),
                station.kwhPriceTL?.let { "Üyeliksiz DC fiyatı ${formatPrice(it)}/kWh" } ?: "Fiyat bilinmiyor (ortalama ~14,24 ₺/kWh)"
            )
        } else {
            listOfNotNull(
                station.highway.takeIf { it.isNotBlank() },
                station.openingHours?.let { "Çalışma saati: $it" },
                if (station.hasLpg == true) "Otogaz var" else null
            )
        }
        details.filter { it.isNotBlank() }.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onNavigate, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Directions, null, tint = Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Yol tarifi", color = Accent)
            }
            Button(
                onClick = onPlanTo,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
            ) {
                Icon(Icons.Default.Route, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rota planla")
            }
        }
    }
}
