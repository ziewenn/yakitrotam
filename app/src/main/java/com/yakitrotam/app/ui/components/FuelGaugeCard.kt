package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.model.VehicleProfile
import com.yakitrotam.app.ui.theme.DarkBackground
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurface
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.FuelAmber
import com.yakitrotam.app.ui.theme.PrimaryBlue
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
    var consumptionText by remember {
        mutableStateOf(formatDecimal(vehicleProfile.consumptionPer100Km))
    }
    var capacityText by remember {
        mutableStateOf(vehicleProfile.tankCapacityLiters.toInt().toString())
    }

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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DirectionsCar, null, tint = PrimaryBlue)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Araç profili",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Menzil hesabını kişiselleştir", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                Surface(color = levelColor.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            "${vehicleProfile.calculateCurrentSafeRangeKm().toInt()} km",
                            color = levelColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text("güvenli menzil", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumericField(
                    value = consumptionText,
                    onValueChange = { if (isDecimalInput(it)) consumptionText = it },
                    onCommit = ::commitConsumption,
                    label = "ORT. TÜKETİM",
                    suffix = "L / 100 km",
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
                        label = { Text(formatDecimal(preset), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DarkSurfaceVariant,
                            selectedContainerColor = PrimaryBlue.copy(alpha = 0.18f),
                            labelColor = TextSecondary,
                            selectedLabelColor = PrimaryBlue
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = DarkBorder,
                            selectedBorderColor = PrimaryBlue
                        )
                    )
                }
            }

            HorizontalDivider(color = DarkBorder)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("MEVCUT YAKIT", color = TextMuted, fontSize = 11.sp, letterSpacing = 0.8.sp)
                        Text(
                            "${formatDecimal(vehicleProfile.currentFuelLiters)} litre",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Text(
                        "%${levelPercent.toInt()}",
                        color = levelColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(DarkBackground)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(levelPercent / 100f).fillMaxHeight()
                            .clip(CircleShape).background(levelColor)
                    )
                }
                Slider(
                    value = levelPercent,
                    onValueChange = {
                        onUpdateProfile(vehicleProfile.copy(currentLevelPercent = it.toDouble()))
                    },
                    valueRange = 5f..100f,
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryBlue,
                        activeTrackColor = PrimaryBlue,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("YAKIT TÜRÜ", color = TextMuted, fontSize = 11.sp, letterSpacing = 0.8.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FuelType.entries.forEach { fuelType ->
                        val selected = vehicleProfile.fuelType == fuelType
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateProfile(vehicleProfile.copy(fuelType = fuelType)) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.LocalGasStation, null, Modifier.size(16.dp)) }
                            } else null,
                            label = {
                                Text(
                                    when (fuelType) {
                                        FuelType.BENZIN -> "Benzin"
                                        FuelType.DIZEL -> "Motorin"
                                        FuelType.LPG -> "LPG"
                                    },
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceVariant,
                                selectedContainerColor = PrimaryBlue,
                                labelColor = TextSecondary,
                                selectedLabelColor = DarkBackground,
                                selectedLeadingIconColor = DarkBackground
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = DarkBorder
                            )
                        )
                    }
                }
            }
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
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onCommit() }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DarkSurfaceVariant,
            unfocusedContainerColor = DarkSurfaceVariant,
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = DarkBorder,
            focusedLabelColor = PrimaryBlue,
            unfocusedLabelColor = TextMuted,
            cursorColor = PrimaryBlue
        )
    )
}

private fun isDecimalInput(value: String): Boolean =
    value.length <= 6 && value.matches(Regex("^\\d{0,3}([.,]\\d{0,2})?$"))

private fun parseDecimal(value: String): Double? = value.replace(',', '.').toDoubleOrNull()

private fun formatDecimal(value: Double): String =
    String.format(Locale.US, if (value % 1.0 == 0.0) "%.0f" else "%.1f", value)
