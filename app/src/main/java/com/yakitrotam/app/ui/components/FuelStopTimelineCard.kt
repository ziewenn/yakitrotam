package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.FuelStop
import com.yakitrotam.app.data.model.StopAlternative
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

private val RailWidth = 28.dp

/** "Yol üstü" ya da gerçek ek yol: "+4,2 km · +6 dk". */
private fun detourLabel(extraKm: Double, minutes: Double?): String =
    if (extraKm < 1.0) "Yol üstü" else buildString {
        append("+${formatDecimal(extraKm)} km")
        if (minutes != null && minutes >= 1.0) append(" · +${minutes.toInt()} dk")
    }

/** Zaman çizelgesinin sol rayı: işaret + aşağı doğru devam eden ince çizgi. */
@Composable
fun TimelineRow(
    marker: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawLineAbove: Boolean = true,
    drawLineBelow: Boolean = true,
    content: @Composable () -> Unit
) {
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.width(RailWidth).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.width(2.dp).height(14.dp).background(if (drawLineAbove) DarkBorder else Color.Transparent))
            marker()
            Box(Modifier.width(2.dp).weight(1f).background(if (drawLineBelow) DarkBorder else Color.Transparent))
        }
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f).padding(bottom = if (drawLineBelow) 12.dp else 0.dp)) { content() }
    }
}

/** Kalkış ve varış satırları. */
@Composable
fun TimelineEndpoint(
    title: String,
    caption: String,
    color: Color,
    isStart: Boolean
) {
    TimelineRow(
        marker = { Box(Modifier.size(12.dp).clip(CircleShape).background(color)) },
        drawLineAbove = !isStart,
        drawLineBelow = isStart
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
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

/** Tek bir yakıt durağı: ne zaman, nerede, ne kadar yakıt, kaç para. */
@Composable
fun FuelStopTimelineCard(
    stop: FuelStop,
    onNavigateToStop: (FuelStop) -> Unit,
    onSelectAlternative: (StopAlternative) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAlternatives by remember(stop.station.id) { mutableStateOf(false) }
    val fuelPercent = stop.arrivalFuelLevelPercent
    val levelColor = when {
        fuelPercent <= 12.0 -> ReserveRed
        fuelPercent <= 25.0 -> FuelAmber
        else -> SafeGreen
    }
    val onRoute = stop.extraRoadKm < 1.0

    TimelineRow(
        modifier = modifier,
        marker = {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("${stop.stopIndex}", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(CardShape).background(DarkSurface).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandBadge(brand = stop.station.brand, size = 40)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stop.station.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf("${stop.distanceFromOriginKm.toInt()}. km", stop.station.city)
                            .filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    detourLabel(stop.extraRoadKm, stop.detourMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (onRoute) SafeGreen else FuelAmber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background((if (onRoute) SafeGreen else FuelAmber).copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Fact("Alınacak yakıt", "${formatDecimal(stop.refuelLiters)} L")
                Fact("Tahmini tutar", formatMoney(stop.estimatedRefuelCostTL))
                Fact("Varışta depo", "%${fuelPercent.toInt()}", valueColor = levelColor)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onNavigateToStop(stop) }) {
                    Icon(Icons.Default.Directions, null, tint = AccentLime, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Yol tarifi", color = AccentLime, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.weight(1f))
                if (stop.alternatives.isNotEmpty()) {
                    TextButton(onClick = { showAlternatives = !showAlternatives }) {
                        Text("Başka istasyon", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
                        Icon(
                            if (showAlternatives) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showAlternatives) "Gizle" else "Göster",
                            tint = TextSecondary
                        )
                    }
                }
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

@Composable
private fun Fact(label: String, value: String, valueColor: Color = TextPrimary) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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
                "$position · ${detourLabel(alternative.extraRoadKm, alternative.detourMinutes).lowercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("Seç", style = MaterialTheme.typography.titleMedium, color = AccentLime)
    }
}
