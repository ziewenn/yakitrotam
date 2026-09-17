package com.yakitrotam.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.ads.NativeAdCard
import com.yakitrotam.app.data.model.TripPlanResult
import com.yakitrotam.app.ui.components.CardShape
import com.yakitrotam.app.ui.components.FuelStopTimelineCard
import com.yakitrotam.app.ui.components.RouteMiniMap
import com.yakitrotam.app.ui.components.SurfaceCard
import com.yakitrotam.app.ui.components.TimelineEndpoint
import com.yakitrotam.app.ui.components.TimelineRow
import com.yakitrotam.app.ui.components.formatDecimal
import com.yakitrotam.app.ui.components.formatDuration
import com.yakitrotam.app.ui.components.formatMoney
import com.yakitrotam.app.ui.components.formatPrice
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBackground
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.FuelAmber
import com.yakitrotam.app.ui.theme.ReserveRed
import com.yakitrotam.app.ui.theme.SafeGreen
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import com.yakitrotam.app.util.GoogleMapsLauncher
import com.yakitrotam.app.util.TripShareText

/**
 * Plan sonucu. Sıra, sürücünün sorduğu sırayı izler: nereden geçiyorum (harita), ne kadar
 * sürer ve tutar (özet), nerede duruyorum (duraklar), ayrıntı isteyene maliyet dökümü.
 */
@Composable
fun RouteSummaryScreen(
    tripResult: TripPlanResult,
    onBackToPlanner: () -> Unit,
    onSelectAlternative: (stopIndex: Int, stationId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToPlanner) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextPrimary)
                }
                Text(
                    text = "${tripResult.origin.name.substringBefore(" (")} → " +
                        tripResult.destination.name.substringBefore(" ("),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { shareTripPlan(context, tripResult) }) {
                    Icon(Icons.Default.Share, "Planı paylaş", tint = TextPrimary)
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Button(
                    onClick = { GoogleMapsLauncher.launchFullRouteInGoogleMaps(context, tripResult) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Navigation, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (tripResult.hasStops) "Duraklarla Haritalar'da aç" else "Haritalar'da aç",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
        ) {
            item { RouteMiniMap(tripResult = tripResult) }

            item { TripOverviewCard(tripResult, Modifier.padding(top = 12.dp)) }

            tripResult.warning?.let { warning ->
                item { Notice(warning, FuelAmber, Modifier.padding(top = 12.dp)) }
            }

            item {
                Text(
                    if (tripResult.hasStops) "Yakıt durakları" else "Yakıt durağı gerekmiyor",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp)
                )
            }

            if (!tripResult.hasStops) {
                item {
                    Notice(
                        "Rezerve düşmeden varış noktasına ulaşırsın. Varışta depoda yaklaşık " +
                            "${formatDecimal(tripResult.arrivalFuelLiters)} L kalır.",
                        SafeGreen,
                        icon = Icons.Default.CheckCircle
                    )
                }
                // Reklam sayfanın üst yarısında kalır; durak olmasa da gösterim kaybolmaz.
                item { NativeAdCard(Modifier.padding(top = 12.dp)) }
            } else {
                item {
                    TimelineEndpoint(
                        title = tripResult.origin.name,
                        caption = "Depoda ${formatDecimal(tripResult.vehicleProfile.currentFuelLiters)} L " +
                            "(%${tripResult.vehicleProfile.currentLevelPercent.toInt()})",
                        color = SafeGreen,
                        isStart = true
                    )
                }
                itemsIndexed(tripResult.stops, key = { _, stop -> stop.station.id }) { index, stop ->
                    FuelStopTimelineCard(
                        stop = stop,
                        onNavigateToStop = { GoogleMapsLauncher.launchTurnByTurnToStation(context, it) },
                        onSelectAlternative = { onSelectAlternative(stop.stopIndex, it.station.id) }
                    )
                    // Liste içi native reklam: ilk duraktan sonra, zaman çizelgesi rayı kesilmeden.
                    if (index == 0) {
                        NativeAdCard { ad -> TimelineRow(marker = {}) { ad() } }
                    }
                }
                item {
                    TimelineEndpoint(
                        title = tripResult.destination.name,
                        caption = "Depoda ~${formatDecimal(tripResult.arrivalFuelLiters)} L " +
                            "(%${tripResult.arrivalFuelPercent.toInt()}) kalır",
                        color = ReserveRed,
                        isStart = false
                    )
                }
            }

            item { CostBreakdownCard(tripResult, Modifier.padding(top = 24.dp)) }

            item {
                Text(
                    "İstasyonlar: OpenStreetMap katkıcıları · Fiyatlar: ${tripResult.fuelPrice.sourceName}, " +
                        "${tripResult.fuelPrice.priceDate} · Rota: OSRM. Tüm değerler tahmindir.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp)
                )
            }
        }
    }
}

/** Üç temel sayı ve cebinden çıkacak para; tek bakışta okunur. */
@Composable
private fun TripOverviewCard(tripResult: TripPlanResult, modifier: Modifier = Modifier) {
    SurfaceCard(modifier = modifier, spacing = 14.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Mesafe", "${tripResult.totalDistanceKm.toInt()} km")
            Metric("Süre", formatDuration(tripResult.estimatedDrivingTimeMinutes))
            Metric("Durak", if (tripResult.hasStops) "${tripResult.stopsCount}" else "Yok")
        }
        HorizontalDivider(color = DarkBorder)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text("Duraklarda ödeyeceğin", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Text(
                    "~${formatMoney(tripResult.totalRefuelCostTL)}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Yakacağın yakıt", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Text(
                    "${formatDecimal(tripResult.totalFuelConsumedLiters)} L",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
    }
}

@Composable
private fun CostBreakdownCard(tripResult: TripPlanResult, modifier: Modifier = Modifier) {
    val fuelType = tripResult.vehicleProfile.fuelType
    val price = tripResult.fuelPrice

    SurfaceCard(modifier = modifier) {
        Text("Maliyet ayrıntısı", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        CostRow(
            "Yolculuğun yakıt maliyeti",
            "${formatDecimal(tripResult.totalFuelConsumedLiters)} L × ${formatPrice(price.priceFor(fuelType))}",
            formatMoney(tripResult.totalEstimatedCostTL)
        )
        CostRow(
            "Duraklarda alınacak yakıt",
            "${formatDecimal(tripResult.stops.sumOf { it.refuelLiters })} L, son durakta yetecek kadar",
            formatMoney(tripResult.totalRefuelCostTL)
        )
        CostRow(
            "100 km başına",
            "${formatDecimal(tripResult.vehicleProfile.consumptionPer100Km)} L/100 km tüketimle",
            formatMoney(
                if (tripResult.totalDrivenDistanceKm > 0) {
                    tripResult.totalEstimatedCostTL / tripResult.totalDrivenDistanceKm * 100.0
                } else 0.0
            )
        )
        if (price.isEstimated(fuelType)) {
            Text(
                "${fuelType.displayName} fiyatı canlı yayınlanmadığı için tahminidir; gerçek pompa fiyatı farklı olabilir.",
                style = MaterialTheme.typography.bodyMedium,
                color = FuelAmber
            )
        }
    }
}

@Composable
private fun CostRow(label: String, caption: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
    }
}

@Composable
private fun Notice(
    message: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.ErrorOutline
) {
    Row(
        modifier = modifier.fillMaxWidth().clip(CardShape).background(color.copy(alpha = 0.12f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(message, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Planı düz metin olarak sistemin paylaşım menüsüne verir (WhatsApp, SMS, e-posta...). */
private fun shareTripPlan(context: Context, trip: TripPlanResult) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "YakıtRotam yakıt planı")
        putExtra(Intent.EXTRA_TEXT, TripShareText.build(trip))
    }
    context.startActivity(Intent.createChooser(send, "Yakıt planını paylaş"))
}
