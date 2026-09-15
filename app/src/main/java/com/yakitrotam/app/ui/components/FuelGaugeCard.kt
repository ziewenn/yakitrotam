package com.yakitrotam.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.model.VehicleProfile
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.FuelAmber
import com.yakitrotam.app.ui.theme.ReserveRed
import com.yakitrotam.app.ui.theme.SafeGreen
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun FuelGaugeCard(
    vehicleProfile: VehicleProfile,
    onUpdateProfile: (VehicleProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val levelPercent = vehicleProfile.currentLevelPercent.toFloat()
    val levelColor = when {
        levelPercent <= vehicleProfile.reserveThresholdPercent -> ReserveRed
        levelPercent <= 30f -> FuelAmber
        else -> SafeGreen
    }
    var consumptionText by remember { mutableStateOf(formatDecimal(vehicleProfile.consumptionPer100Km)) }
    var capacityText by remember { mutableStateOf(formatDecimal(vehicleProfile.tankCapacityLiters)) }

    fun commitConsumption() {
        parseDecimal(consumptionText)?.coerceIn(2.0, 30.0)?.let {
            onUpdateProfile(vehicleProfile.copy(consumptionPer100Km = it))
            consumptionText = formatDecimal(it)
        } ?: run { consumptionText = formatDecimal(vehicleProfile.consumptionPer100Km) }
    }

    fun commitCapacity() {
        parseDecimal(capacityText)?.coerceIn(10.0, 150.0)?.let {
            onUpdateProfile(vehicleProfile.copy(tankCapacityLiters = it))
            capacityText = formatDecimal(it)
        } ?: run { capacityText = formatDecimal(vehicleProfile.tankCapacityLiters) }
    }

    SectionCard(
        modifier = modifier,
        title = "Araç profili",
        icon = Icons.Default.DirectionsCar
    ) {
        // Yakıt göstergesi: yarım ay kadran + yanında menzil bilgisi
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FuelArcGauge(
                percent = levelPercent,
                reservePercent = vehicleProfile.reserveThresholdPercent.toFloat(),
                color = levelColor,
                modifier = Modifier.size(140.dp, 104.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    Text("GÜVENLİ MENZİL", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Text(
                        "${vehicleProfile.calculateCurrentSafeRangeKm().toInt()} km",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                }
                Text(
                    "Depoda ${formatDecimal(vehicleProfile.currentFuelLiters)} L · " +
                        "rezerv ${formatDecimal(vehicleProfile.reserveLiters)} L",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Slider(
                    value = levelPercent,
                    onValueChange = { onUpdateProfile(vehicleProfile.copy(currentLevelPercent = it.toDouble())) },
                    valueRange = 5f..100f,
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = levelColor,
                        activeTrackColor = levelColor,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
            }
        }

        HorizontalDivider(color = DarkBorder)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumericField(
                value = consumptionText,
                onValueChange = { if (isDecimalInput(it)) consumptionText = it },
                onCommit = ::commitConsumption,
                label = "ORT. TÜKETİM",
                suffix = "L/100km",
                modifier = Modifier.weight(1f)
            )
            NumericField(
                value = capacityText,
                onValueChange = { if (isDecimalInput(it)) capacityText = it },
                onCommit = ::commitCapacity,
                label = "DEPO HACMİ",
                suffix = "litre",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(5.5, 6.8, 8.0, 10.0).forEach { preset ->
                val selected = kotlin.math.abs(vehicleProfile.consumptionPer100Km - preset) < 0.01
                FilterChip(
                    selected = selected,
                    onClick = {
                        consumptionText = formatDecimal(preset)
                        onUpdateProfile(vehicleProfile.copy(consumptionPer100Km = preset))
                        focusManager.clearFocus()
                    },
                    label = {
                        Text(
                            formatDecimal(preset),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(11.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = DarkSurfaceVariant,
                        selectedContainerColor = AccentLime.copy(alpha = 0.16f),
                        labelColor = TextSecondary,
                        selectedLabelColor = AccentLime
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = DarkBorder,
                        selectedBorderColor = AccentLime
                    )
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("YAKIT TÜRÜ", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FuelType.entries.forEach { fuelType ->
                    val selected = vehicleProfile.fuelType == fuelType
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateProfile(vehicleProfile.copy(fuelType = fuelType)) },
                        label = {
                            Text(
                                when (fuelType) {
                                    FuelType.BENZIN -> "Benzin"
                                    FuelType.DIZEL -> "Motorin"
                                    FuelType.LPG -> "LPG"
                                },
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(11.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DarkSurfaceVariant,
                            selectedContainerColor = AccentLime,
                            labelColor = TextSecondary,
                            selectedLabelColor = Color.Black
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = DarkBorder,
                            selectedBorderColor = AccentLime
                        )
                    )
                }
            }
        }
    }
}

/**
 * 200° açıklığında yakıt kadranı. Rezerv bölgesi kırmızı tarama ile işaretlenir,
 * böylece sürücü "hangi noktadan sonra durak şart" sorusunu tek bakışta görür.
 */
@Composable
private fun FuelArcGauge(
    percent: Float,
    reservePercent: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedPercent by animateFloatAsState(
        targetValue = percent.coerceIn(0f, 100f),
        animationSpec = tween(450),
        label = "fuelLevel"
    )

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val diameter = size.width - stroke
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(diameter, diameter)
            val startAngle = 170f
            val sweepTotal = 200f

            drawArc(
                color = DarkSurfaceVariant,
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Rezerv bölgesi
            drawArc(
                color = ReserveRed.copy(alpha = 0.35f),
                startAngle = startAngle,
                sweepAngle = sweepTotal * (reservePercent / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Mevcut seviye
            drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color, color)),
                startAngle = startAngle,
                sweepAngle = sweepTotal * (animatedPercent / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(
                "%${animatedPercent.toInt()}",
                style = MaterialTheme.typography.displaySmall,
                color = color
            )
            Text("DEPO", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
    }
}

@Composable
private fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    label: String,
    suffix: String,
    modifier: Modifier = Modifier
) {
    var hadFocus by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged {
            if (hadFocus && !it.isFocused) onCommit()
            hadFocus = it.isFocused
        },
        label = { Text(label, fontSize = 10.sp, letterSpacing = 0.5.sp) },
        suffix = { Text(suffix, color = TextMuted, fontSize = 11.sp) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = TextPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit() }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DarkSurfaceVariant,
            unfocusedContainerColor = DarkSurfaceVariant,
            focusedBorderColor = AccentLime,
            unfocusedBorderColor = DarkBorder,
            focusedLabelColor = AccentLime,
            unfocusedLabelColor = TextMuted,
            cursorColor = AccentLime
        )
    )
}

private fun isDecimalInput(value: String): Boolean =
    value.length <= 6 && value.matches(Regex("^\\d{0,3}([.,]\\d{0,2})?$"))

private fun parseDecimal(value: String): Double? = value.replace(',', '.').toDoubleOrNull()

internal fun formatDecimal(value: Double): String =
    String.format(Locale.US, if (value % 1.0 == 0.0) "%.0f" else "%.1f", value)

/** Sıkıştırılmamış, dile uygun para biçimi: 12.480 ₺ */
internal fun formatMoney(value: Double): String =
    String.format(Locale.forLanguageTag("tr"), "%,.0f ₺", value)

internal fun formatPrice(value: Double): String =
    String.format(Locale.forLanguageTag("tr"), "%.2f ₺", value)
