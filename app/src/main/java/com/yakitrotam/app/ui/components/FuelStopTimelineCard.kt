package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakitrotam.app.data.model.FuelStop
import com.yakitrotam.app.ui.theme.*

@Composable
fun FuelStopTimelineCard(
    stop: FuelStop,
    onNavigateToStop: (FuelStop) -> Unit,
    modifier: Modifier = Modifier
) {
    val brand = stop.station.brand
    val brandPrimary = Color(brand.primaryColorHex)

    val fuelPercent = stop.arrivalFuelLevelPercent.toInt()
    val isCritical = fuelPercent <= 18

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isCritical) ReserveRed.copy(alpha = 0.5f) else DarkBorder
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Üst Satır: Durak Sırası, Marka Rozeti ve İstasyon Adı
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Durak Numarası Dairesi
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.15f))
                        .border(1.dp, PrimaryBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${stop.stopIndex}",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryBlue,
                        fontSize = 14.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Marka etiketi
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = brandPrimary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = brand.displayName,
                                color = if (brand.primaryColorHex == 0xFFFFD700) Color.Black else Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (stop.station.city.isNotEmpty()) {
                            Text(
                                text = stop.station.city,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = stop.station.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }
            }

            // Otoyol / Lokasyon Bilgisi
            if (stop.station.highway.isNotEmpty()) {
                Text(
                    text = "Güzergah: ${stop.station.highway}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

            // İstatistikler Grid'i (Kalan Yakıt, Alınacak Yakıt, Maliyet)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Varıştaki Kalan Yakıt
                Column {
                    Text(
                        text = "Varışta Kalan",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "%$fuelPercent (${"%.1f".format(stop.arrivalFuelLiters)} L)",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCritical) ReserveRed else FuelAmber,
                        fontSize = 13.sp
                    )
                }

                // Dolum Miktarı
                Column {
                    Text(
                        text = "Dolum Miktarı",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "+${"%.1f".format(stop.refuelLiters)} L",
                        style = MaterialTheme.typography.titleMedium,
                        color = SafeGreen,
                        fontSize = 13.sp
                    )
                }

                // Yaklaşık Maliyet
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Yaklaşık Tutar",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "~${stop.estimatedRefuelCostTL.toInt()} ₺",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                }
            }

            // Sapma Mesafesi & Hızlı Navigasyon Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (stop.detourDistanceKm < 0.5) "Yol üstü (0 km sapma)" else "~${"%.1f".format(stop.detourDistanceKm)} km sapma",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    fontSize = 11.sp
                )

                OutlinedButton(
                    onClick = { onNavigateToStop(stop) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(PrimaryBlue.copy(alpha = 0.5f))
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Maps'te Aç", fontSize = 11.sp)
                }
            }
        }
    }
}
