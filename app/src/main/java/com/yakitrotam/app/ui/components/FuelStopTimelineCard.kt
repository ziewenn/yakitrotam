package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yakitrotam.app.data.model.StopAlternative
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.FuelStop
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurface
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.FuelAmber
import com.yakitrotam.app.ui.theme.ReserveRed
import com.yakitrotam.app.ui.theme.SafeGreen
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary

/**
 * Tek bir yakıt durağı. Sol tarafta zaman çizelgesi rayı, sağda istasyon kartı;
 * [isLast] false ise ray aşağıya doğru devam eder.
 */
@Composable
fun FuelStopTimelineCard(
    stop: FuelStop,
    fuelType: FuelType,
    onNavigateToStop: (FuelStop) -> Unit,
    onSelectAlternative: (StopAlternative) -> Unit,
    modifier: Modifier = Modifier,
    isLast: Boolean = false
) {
    var showAlternatives by remember(stop.station.id) { mutableStateOf(false) }
    val fuelPercent = stop.arrivalFuelLevelPercent
    val isCritical = fuelPercent <= 12.0
    val levelColor = when {
        isCritical -> ReserveRed
        fuelPercent <= 25.0 -> FuelAmber
        else -> SafeGreen
    }

    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Zaman çizelgesi rayı
        Column(
            modifier = Modifier.width(34.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(AccentLime),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${stop.stopIndex}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Black
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(DarkBorder)
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(
                    width = 1.dp,
                    color = if (isCritical) ReserveRed.copy(alpha = 0.55f) else DarkBorder,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                BrandBadge(brand = stop.station.brand, size = 40)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.station.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("${stop.distanceFromOriginKm.toInt()}. km")
                            if (stop.station.city.isNotBlank()) append(" · ${stop.station.city}")
                            if (stop.station.highway.isNotBlank()) append(" · ${stop.station.highway}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Varışta kalan yakıt çubuğu
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "VARIŞTA DEPO",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Text(
                        "%${fuelPercent.toInt()} · ${formatDecimal(stop.arrivalFuelLiters)} L",
                        style = MaterialTheme.typography.titleMedium,
                        color = levelColor
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((fuelPercent / 100.0).toFloat().coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(levelColor)
                    )
                }
            }

            HorizontalDivider(color = DarkBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatTile(
                    label = "Bu etap",
                    value = "${stop.legDistanceKm.toInt()} km",
                    caption = if (stop.detourDistanceKm < 0.4) {
                        "yol üstü"
                    } else {
                        "${formatDecimal(stop.detourDistanceKm)} km sapma"
                    }
                )
                StatTile(
                    label = "Dolum",
                    value = "${formatDecimal(stop.refuelLiters)} L",
                    valueColor = AccentLime,
                    caption = fuelType.displayName.substringBefore(" ")
                )
                StatTile(
                    label = "Tutar",
                    value = formatMoney(stop.estimatedRefuelCostTL),
                    alignment = Alignment.End,
                    caption = "tahmini"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                stop.station.openingHours?.let { hours ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (hours.contains("24/7")) "24 saat açık" else hours,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                } ?: Spacer(Modifier.width(1.dp))

                OutlinedButton(
                    onClick = { onNavigateToStop(stop) },
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentLime),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(AccentLime.copy(alpha = 0.45f))
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Directions, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Yol tarifi", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (stop.alternatives.isNotEmpty()) {
                HorizontalDivider(color = DarkBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showAlternatives = !showAlternatives }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SwapHoriz, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Başka istasyon (${stop.alternatives.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (showAlternatives) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showAlternatives) "Gizle" else "Göster",
                        tint = TextSecondary
                    )
                }

                if (showAlternatives) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stop.alternatives.forEach { alternative ->
                            AlternativeRow(
                                alternative = alternative,
                                currentKm = stop.distanceFromOriginKm,
                                onClick = {
                                    showAlternatives = false
                                    onSelectAlternative(alternative)
                                }
                            )
                        }
                        Text(
                            "Seçince sonraki duraklar yeni istasyona göre yeniden hesaplanır.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

/** Zaman çizelgesinin başı ve sonu: kalkış / varış satırı. */
@Composable
fun TimelineEndpoint(
    label: String,
    title: String,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier,
    isStart: Boolean = true
) {
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.width(34.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isStart) {
                Box(modifier = Modifier.width(2.dp).height(14.dp).background(DarkBorder))
            }
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(color.copy(alpha = 0.18f))
                    .border(2.dp, color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Navigation, null, tint = color, modifier = Modifier.size(12.dp))
            }
            if (isStart) {
                Box(modifier = Modifier.width(2.dp).weight(1f).background(DarkBorder))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isStart) 12.dp else 0.dp, top = 2.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun AlternativeRow(
    alternative: StopAlternative,
    currentKm: Double,
    onClick: () -> Unit
) {
    val kmDifference = (alternative.distanceFromOriginKm - currentKm).toInt()
    val position = when {
        kmDifference > 0 -> "$kmDifference km ileride"
        kmDifference < 0 -> "${-kmDifference} km önce"
        else -> "aynı noktada"
    }
    val detour = if (alternative.detourDistanceKm < 0.4) {
        "yol üstü"
    } else {
        "${formatDecimal(alternative.detourDistanceKm)} km sapma"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BrandBadge(brand = alternative.station.brand, size = 32)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                alternative.station.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${alternative.distanceFromOriginKm.toInt()}. km · $position · $detour",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("Seç", style = MaterialTheme.typography.titleMedium, color = AccentLime)
    }
}
