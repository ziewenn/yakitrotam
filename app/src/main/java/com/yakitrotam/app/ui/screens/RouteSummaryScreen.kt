package com.yakitrotam.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakitrotam.app.data.model.FuelStop
import com.yakitrotam.app.data.model.TripPlanResult
import com.yakitrotam.app.ui.components.FuelStopTimelineCard
import com.yakitrotam.app.ui.components.RouteMiniMap
import com.yakitrotam.app.ui.theme.*
import com.yakitrotam.app.util.GoogleMapsLauncher

@Composable
fun RouteSummaryScreen(
    tripResult: TripPlanResult,
    onBackToPlanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hours = tripResult.estimatedDrivingTimeMinutes / 60
    val minutes = tripResult.estimatedDrivingTimeMinutes % 60

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        bottomBar = {
            // Google Maps'te Aç Sabit Buton Barı
            Surface(
                color = DarkSurface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            GoogleMapsLauncher.launchFullRouteInGoogleMaps(context, tripResult)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafeGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Google Maps'te Aç & Başlat",
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.sp
                        )
                    }

                    Text(
                        text = "Tüm yakıt istasyonları rotanıza otomatik durak (waypoint) olarak eklenecektir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            // Geri Dön Butonu ve Başlık
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBackToPlanner,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextPrimary
                        )
                    }

                    Column {
                        Text(
                            text = "Hesaplanan Seyahat Özeti",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "${tripResult.origin.name.substringBefore(" ")} ➔ ${tripResult.destination.name.substringBefore(" ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Temel Rota Metrikleri Kartı
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Mesafe
                            Column {
                                Text(text = "Toplam Mesafe", style = MaterialTheme.typography.labelLarge, color = TextMuted, fontSize = 11.sp)
                                Text(text = "${tripResult.totalDistanceKm.toInt()} km", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                            }
                            // Süre
                            Column {
                                Text(text = "Tahmini Süre", style = MaterialTheme.typography.labelLarge, color = TextMuted, fontSize = 11.sp)
                                Text(text = "${hours}s ${minutes}dk", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                            }
                            // Durak Sayısı
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Yakıt Molası", style = MaterialTheme.typography.labelLarge, color = TextMuted, fontSize = 11.sp)
                                Text(
                                    text = if (tripResult.stopsCount > 0) "${tripResult.stopsCount} Durak" else "0 Durak",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = if (tripResult.stopsCount > 0) FuelAmber else SafeGreen
                                )
                            }
                        }

                        HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Toplam Yakıt: ~${"%.1f".format(tripResult.totalFuelConsumedLiters)} L",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Tahmini Tutar: ~${tripResult.totalEstimatedCostTL.toInt()} ₺",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryBlue,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // İnteraktif Rota Önizleme Haritası
            item {
                Text(
                    text = "Rota & İstasyon Önizlemesi",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                RouteMiniMap(tripResult = tripResult)
            }

            // Durak Listesi Başlığı
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (tripResult.hasStops) "Önerilen Yakıt Durakları" else "Durak Bilgisi",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    if (tripResult.hasStops) {
                        Text(
                            text = "Optimum Marka Sıralı",
                            style = MaterialTheme.typography.labelLarge,
                            color = PrimaryBlue,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Eğer durak gerekmiyorsa tebrik kartı
            if (!tripResult.hasStops) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SafeGreen.copy(alpha = 0.12f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SafeGreen.copy(alpha = 0.4f)))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(32.dp))
                            Column {
                                Text(
                                    text = "Yolculuk İçin Yakıtınız Yeterli!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Mevcut deponuz ile rezerve düşmeden doğrudan varış noktanıza ulaşabilirsiniz. Ara durak eklenmedi.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // Hesaplanan her durak kartı
                items(tripResult.stops) { stop ->
                    FuelStopTimelineCard(
                        stop = stop,
                        onNavigateToStop = { selectedStop: FuelStop ->
                            GoogleMapsLauncher.launchTurnByTurnToStation(context, selectedStop)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
