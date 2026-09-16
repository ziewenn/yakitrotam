package com.yakitrotam.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.ads.AdBanner
import com.yakitrotam.app.data.model.FuelStop
import com.yakitrotam.app.data.model.TripPlanResult
import com.yakitrotam.app.ui.components.FuelStopTimelineCard
import com.yakitrotam.app.ui.components.RouteMiniMap
import com.yakitrotam.app.ui.components.SectionCard
import com.yakitrotam.app.ui.components.StatTile
import com.yakitrotam.app.ui.components.TimelineEndpoint
import com.yakitrotam.app.ui.components.formatDecimal
import com.yakitrotam.app.ui.components.formatMoney
import com.yakitrotam.app.ui.components.formatPrice
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBackground
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurface
import com.yakitrotam.app.ui.theme.FuelAmber
import com.yakitrotam.app.ui.theme.HeroGradientEnd
import com.yakitrotam.app.ui.theme.HeroGradientMid
import com.yakitrotam.app.ui.theme.HeroGradientStart
import com.yakitrotam.app.ui.theme.ReserveRed
import com.yakitrotam.app.ui.theme.SafeGreen
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import com.yakitrotam.app.util.GoogleMapsLauncher
import com.yakitrotam.app.util.TripShareText

@Composable
fun RouteSummaryScreen(
    tripResult: TripPlanResult,
    onBackToPlanner: () -> Unit,
    onSelectAlternative: (stopIndex: Int, stationId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hours = tripResult.estimatedDrivingTimeMinutes / 60
    val minutes = tripResult.estimatedDrivingTimeMinutes % 60

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        bottomBar = {
            Surface(color = DarkSurface, tonalElevation = 6.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { shareTripPlan(context, tripResult) },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentLime),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                                .copy(brush = SolidColor(AccentLime.copy(alpha = 0.5f)))
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Paylaş", style = MaterialTheme.typography.titleMedium)
                        }
                        Button(
                            onClick = { GoogleMapsLauncher.launchFullRouteInGoogleMaps(context, tripResult) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentLime,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.Navigation, null, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("Haritalarda başlat", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Text(
                        text = if (tripResult.hasStops) {
                            "${tripResult.stopsCount} yakıt durağı güzergaha ara nokta olarak eklenir."
                        } else {
                            "Durak gerekmediği için rota doğrudan açılır."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    AdBanner()
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.statusBarsPadding().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onBackToPlanner,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SEYAHAT PLANI", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Text(
                            text = "${tripResult.origin.name.substringBefore(" (")} → " +
                                tripResult.destination.name.substringBefore(" ("),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item { TripHeroCard(tripResult, hours, minutes) }

            tripResult.warning?.let { warning ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(FuelAmber.copy(alpha = 0.12f))
                            .border(1.dp, FuelAmber.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = FuelAmber, modifier = Modifier.size(20.dp))
                        Text(warning, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            item { RouteMiniMap(tripResult = tripResult) }

            item { CostBreakdownCard(tripResult) }

            item {
                Text(
                    text = if (tripResult.hasStops) "Yakıt durakları" else "Durak gerekmiyor",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (!tripResult.hasStops) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(SafeGreen.copy(alpha = 0.1f))
                            .border(1.dp, SafeGreen.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = SafeGreen, modifier = Modifier.size(30.dp))
                        Column {
                            Text(
                                "Yakıtınız yeterli",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                "Rezerve düşmeden varış noktasına ulaşabilirsiniz. " +
                                    "Varışta yaklaşık ${formatDecimal(tripResult.arrivalFuelLiters)} L kalır.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                item {
                    TimelineEndpoint(
                        label = "Kalkış",
                        title = tripResult.origin.name,
                        caption = "Depoda ${formatDecimal(tripResult.vehicleProfile.currentFuelLiters)} L " +
                            "(%${tripResult.vehicleProfile.currentLevelPercent.toInt()})",
                        color = SafeGreen,
                        isStart = true
                    )
                }
                items(tripResult.stops) { stop ->
                    FuelStopTimelineCard(
                        stop = stop,
                        fuelType = tripResult.vehicleProfile.fuelType,
                        onNavigateToStop = { selectedStop: FuelStop ->
                            GoogleMapsLauncher.launchTurnByTurnToStation(context, selectedStop)
                        },
                        onSelectAlternative = { alternative ->
                            onSelectAlternative(stop.stopIndex, alternative.station.id)
                        }
                    )
                }
                item {
                    TimelineEndpoint(
                        label = "Varış",
                        title = tripResult.destination.name,
                        caption = "Depoda ~${formatDecimal(tripResult.arrivalFuelLiters)} L " +
                            "(%${tripResult.arrivalFuelPercent.toInt()}) kalır",
                        color = ReserveRed,
                        isStart = false
                    )
                }
            }

            item { DataSourceFooter(tripResult) }
        }
    }
}

@Composable
private fun TripHeroCard(tripResult: TripPlanResult, hours: Int, minutes: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(HeroGradientStart, HeroGradientMid, HeroGradientEnd)))
            .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatTile(
                label = "Mesafe",
                value = "${tripResult.totalDistanceKm.toInt()} km",
                caption = if (tripResult.totalDrivenDistanceKm - tripResult.totalDistanceKm > 0.5) {
                    "+${formatDecimal(tripResult.totalDrivenDistanceKm - tripResult.totalDistanceKm)} km sapma"
                } else {
                    "sapmasız"
                }
            )
            StatTile(
                label = "Süre",
                value = if (hours > 0) "${hours}s ${minutes}dk" else "${minutes}dk",
                caption = "molalar dahil"
            )
            StatTile(
                label = "Durak",
                value = "${tripResult.stopsCount}",
                valueColor = if (tripResult.hasStops) FuelAmber else SafeGreen,
                caption = if (tripResult.hasStops) "yakıt molası" else "gerekmiyor",
                alignment = Alignment.End
            )
        }

        HorizontalDivider(color = DarkBorder)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text("POMPADA ÖDENECEK", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Text(
                    formatMoney(tripResult.totalRefuelCostTL),
                    style = MaterialTheme.typography.displaySmall,
                    color = AccentLime
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("YAKILAN YAKIT", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Text(
                    "${formatDecimal(tripResult.totalFuelConsumedLiters)} L",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun CostBreakdownCard(tripResult: TripPlanResult) {
    val fuelType = tripResult.vehicleProfile.fuelType
    val price = tripResult.fuelPrice

    SectionCard(title = "Maliyet dökümü") {
        CostRow(
            "Yolculuğun yakıt maliyeti",
            formatMoney(tripResult.totalEstimatedCostTL),
            "${formatDecimal(tripResult.totalFuelConsumedLiters)} L × ${formatPrice(price.priceFor(fuelType))}"
        )
        CostRow(
            "Duraklarda alınacak yakıt",
            formatMoney(tripResult.totalRefuelCostTL),
            "${formatDecimal(tripResult.stops.sumOf { it.refuelLiters })} L, son durakta yetecek kadar",
            valueColor = AccentLime
        )
        CostRow(
            "100 km başına",
            formatMoney(
                if (tripResult.totalDrivenDistanceKm > 0) {
                    tripResult.totalEstimatedCostTL / tripResult.totalDrivenDistanceKm * 100.0
                } else {
                    0.0
                }
            ),
            "${formatDecimal(tripResult.vehicleProfile.consumptionPer100Km)} L/100km tüketimle"
        )

        if (price.isEstimated(fuelType)) {
            Text(
                text = "${fuelType.displayName} fiyatı canlı yayınlanmadığı için tahminidir; " +
                    "gerçek pompa fiyatı farklılık gösterebilir.",
                style = MaterialTheme.typography.bodyMedium,
                color = FuelAmber
            )
        }
    }
}

@Composable
private fun CostRow(label: String, value: String, caption: String, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
    }
}

@Composable
private fun DataSourceFooter(tripResult: TripPlanResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("VERİ KAYNAKLARI", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(
            "İstasyonlar: OpenStreetMap katkıcıları (ODbL)",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            "Fiyatlar: ${tripResult.fuelPrice.sourceName} · ${tripResult.fuelPrice.regionName} · " +
                tripResult.fuelPrice.priceDate,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            "Rota: OSRM sürüş rotası. Tüm değerler tahmindir; sürüş tarzı ve trafik sonucu değiştirir.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

/** Planı düz metin olarak sistemin paylaşım menüsüne verir (WhatsApp, SMS, e-posta...). */
private fun shareTripPlan(context: android.content.Context, trip: TripPlanResult) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "YakıtRotam yakıt planı")
        putExtra(android.content.Intent.EXTRA_TEXT, TripShareText.build(trip))
    }
    context.startActivity(android.content.Intent.createChooser(send, "Yakıt planını paylaş"))
}
