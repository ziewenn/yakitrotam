package com.yakitrotam.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.model.VehicleProfile
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurface
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary

fun FuelType.shortName(): String = when (this) {
    FuelType.BENZIN -> "Benzin"
    FuelType.DIZEL -> "Motorin"
    FuelType.LPG -> "LPG"
}

/**
 * Araç bilgileri nadiren değişir; bu yüzden ana ekranda yer kaplamaz, alttan açılan
 * sayfada düzenlenir. İlk açılışta [isFirstRun] ile karşılama metni gösterilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSheet(
    vehicleProfile: VehicleProfile,
    isFirstRun: Boolean,
    onUpdateProfile: (VehicleProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var consumptionText by remember { mutableStateOf(formatDecimal(vehicleProfile.consumptionPer100Km)) }
    var capacityText by remember { mutableStateOf(formatDecimal(vehicleProfile.tankCapacityLiters)) }

    fun commitConsumption() {
        val value = parseDecimal(consumptionText)?.coerceIn(2.0, 30.0) ?: vehicleProfile.consumptionPer100Km
        consumptionText = formatDecimal(value)
        if (value != vehicleProfile.consumptionPer100Km) onUpdateProfile(vehicleProfile.copy(consumptionPer100Km = value))
    }

    fun commitCapacity() {
        val value = parseDecimal(capacityText)?.coerceIn(10.0, 150.0) ?: vehicleProfile.tankCapacityLiters
        capacityText = formatDecimal(value)
        if (value != vehicleProfile.tankCapacityLiters) onUpdateProfile(vehicleProfile.copy(tankCapacityLiters = value))
    }

    ModalBottomSheet(
        onDismissRequest = {
            commitConsumption()
            commitCapacity()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column {
                Text(
                    if (isFirstRun) "Önce aracını tanıyalım" else "Araç bilgileri",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    "Bir kez girmen yeterli; menzil ve durak hesabı bunlara göre yapılır.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Yakıt türü", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FuelType.entries.forEachIndexed { index, fuelType ->
                        SegmentedButton(
                            selected = vehicleProfile.fuelType == fuelType,
                            onClick = { onUpdateProfile(vehicleProfile.copy(fuelType = fuelType)) },
                            shape = SegmentedButtonDefaults.itemShape(index, FuelType.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = AccentLime,
                                activeContentColor = Color.Black,
                                activeBorderColor = AccentLime,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = TextSecondary,
                                inactiveBorderColor = DarkBorder
                            ),
                            icon = {}
                        ) { Text(fuelType.shortName()) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ortalama tüketim", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                NumericField(
                    value = consumptionText,
                    onValueChange = { if (isDecimalInput(it)) consumptionText = it },
                    onCommit = ::commitConsumption,
                    suffix = "L / 100 km"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5.5, 6.8, 8.0, 10.0).forEach { preset ->
                        val selected = kotlin.math.abs(vehicleProfile.consumptionPer100Km - preset) < 0.01
                        FilterChip(
                            selected = selected,
                            onClick = {
                                consumptionText = formatDecimal(preset)
                                onUpdateProfile(vehicleProfile.copy(consumptionPer100Km = preset))
                                focusManager.clearFocus()
                            },
                            label = { Text(formatDecimal(preset)) },
                            colors = chipColors(),
                            border = null
                        )
                    }
                }
                Text(
                    "Aracının yol bilgisayarındaki ortalama değeri yazabilirsin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Depo hacmi", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                NumericField(
                    value = capacityText,
                    onValueChange = { if (isDecimalInput(it)) capacityText = it },
                    onCommit = ::commitCapacity,
                    suffix = "litre"
                )
            }

            Button(
                onClick = {
                    commitConsumption()
                    commitCapacity()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = Color.Black)
            ) { Text("Tamam", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/** Marka tercihi: seçilenler öne alınır; hiçbiri seçili değilse tüm markalar eşit değerlendirilir. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrandSheet(
    selectedBrands: Set<FuelBrand>,
    onToggleBrand: (FuelBrand) -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Marka tercihi", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Text(
                        "Seçtiklerin öne alınır. Menzilde yoksa yolda kalmaman için başka marka önerilir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                TextButton(onClick = onSelectAll, enabled = selectedBrands.isNotEmpty()) {
                    Text("Temizle", color = if (selectedBrands.isEmpty()) TextMuted else AccentLime)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FuelBrand.entries.filter { it != FuelBrand.DIGER }.forEach { brand ->
                    val selected = brand in selectedBrands
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleBrand(brand) },
                        label = { Text(brand.displayName) },
                        leadingIcon = {
                            if (selected) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            else BrandBadge(brand, size = 20)
                        },
                        colors = chipColors(),
                        border = null
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = Color.Black)
            ) { Text("Tamam", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = DarkSurfaceVariant,
    labelColor = TextPrimary,
    selectedContainerColor = AccentLime,
    selectedLabelColor = Color.Black,
    selectedLeadingIconColor = Color.Black
)

@Composable
private fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    suffix: String
) {
    var hadFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().onFocusChanged {
            if (hadFocus && !it.isFocused) onCommit()
            hadFocus = it.isFocused
        },
        suffix = { Text(suffix, color = TextMuted) },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = TextPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit() }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DarkSurfaceVariant,
            unfocusedContainerColor = DarkSurfaceVariant,
            focusedBorderColor = AccentLime,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = AccentLime
        )
    )
}

private fun isDecimalInput(value: String): Boolean =
    value.length <= 6 && value.matches(Regex("^\\d{0,3}([.,]\\d{0,2})?$"))

private fun parseDecimal(value: String): Double? = value.replace(',', '.').toDoubleOrNull()
