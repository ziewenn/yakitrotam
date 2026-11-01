package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.model.VehicleProfile
import com.yakitrotam.app.ui.theme.Accent
import com.yakitrotam.app.ui.theme.AccentSoft
import com.yakitrotam.app.ui.theme.FuelAmber
import com.yakitrotam.app.ui.theme.ReserveRed
import com.yakitrotam.app.ui.theme.SafeGreen
import com.yakitrotam.app.ui.theme.SurfaceMuted
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** Planlayıcının en üstündeki araç kartı; dokununca araç bilgileri sayfası açılır. */
@Composable
fun VehicleCard(
    vehicleProfile: VehicleProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceCard(modifier = modifier, padding = 0.dp, spacing = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (vehicleProfile.isElectric) Icons.Default.EvStation else Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = Accent
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (vehicleProfile.fuelType) {
                        FuelType.BENZIN -> "Benzinli araç"
                        FuelType.DIZEL -> "Dizel araç"
                        FuelType.LPG -> "LPG'li araç"
                        FuelType.ELEKTRIK -> "Elektrikli araç"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                Text(
                    vehicleProfile.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    vehicleSummary(vehicleProfile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Değiştir", style = MaterialTheme.typography.labelLarge, color = Accent)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Accent)
            }
        }
    }
}

fun vehicleSummary(profile: VehicleProfile): String = with(profile) {
    if (isElectric) {
        "${formatDecimal(tankCapacityLiters)} kWh · ${formatDecimal(consumptionPer100Km)} kWh/100 km · " +
            "${connector.label} ${formatDecimal(maxChargeKw)} kW"
    } else {
        "${formatDecimal(consumptionPer100Km)} L/100 km · ${formatDecimal(tankCapacityLiters)} L depo"
    }
}

/**
 * Elektrikli aracın enerji kartı: şu anki şarj ve varışta istenen en az şarj. Yakıtlı araçtaki
 * yakıt kadranının karşılığıdır; elektrikte iki değer birden plana girer.
 */
@Composable
fun BatteryCard(
    vehicleProfile: VehicleProfile,
    onUpdate: (VehicleProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val current = vehicleProfile.currentLevelPercent.toFloat()
    val levelColor = when {
        current <= vehicleProfile.reserveThresholdPercent -> ReserveRed
        current <= 30f -> FuelAmber
        else -> SafeGreen
    }

    SurfaceCard(modifier = modifier, spacing = 6.dp) {
        Text("Batarya", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("Şarj seviyesini aracındaki gibi ayarla", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

        PercentSlider(
            label = "Şu anki şarj",
            value = current,
            range = 5f..100f,
            valueColor = levelColor,
            onChange = { onUpdate(vehicleProfile.copy(currentLevelPercent = it.toDouble())) }
        )
        PercentSlider(
            label = "Varışta en az",
            value = vehicleProfile.evArrivalPercent.toFloat(),
            range = 5f..80f,
            valueColor = TextPrimary,
            onChange = { onUpdate(vehicleProfile.copy(evArrivalPercent = it.toDouble())) }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(SurfaceMuted)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Şarj durağına %${vehicleProfile.reserveThresholdPercent.roundToInt()} kalana kadar",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "~${vehicleProfile.calculateCurrentSafeRangeKm().roundToInt()} km",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun PercentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueColor: Color,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("%${value.roundToInt()}", style = MaterialTheme.typography.titleLarge, color = valueColor)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = { onChange(it.roundToInt().toFloat()) },
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = label },
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = SurfaceMuted
            )
        )
    }
}
