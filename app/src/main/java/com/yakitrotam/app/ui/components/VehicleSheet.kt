package com.yakitrotam.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.yakitrotam.app.data.model.EvPresets
import com.yakitrotam.app.data.model.EvPreset
import com.yakitrotam.app.data.model.DcConnector
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.ArrivalFuel
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.data.model.KNOWN_OPERATORS
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.model.VehicleProfile
import com.yakitrotam.app.ui.theme.Accent
import com.yakitrotam.app.ui.theme.OnAccent
import com.yakitrotam.app.ui.theme.BorderColor
import com.yakitrotam.app.ui.theme.CardSurface
import com.yakitrotam.app.ui.theme.SurfaceMuted
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary

/**
 * Araç bilgileri nadiren değişir; bu yüzden ana ekranda yer kaplamaz, alttan açılan
 * sayfada düzenlenir. İlk açılışta [isFirstRun] ile karşılama metni gösterilir.
 * Elektrikli araçta depo yerine batarya, litre yerine kWh sorulur ve hazır model listesi çıkar.
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
    val isEv = vehicleProfile.isElectric
    // Alanlar kayıtlı değere bağlı: yakıt türü değişip profil yer değiştirince ya da hazır model
    // seçilince yeni değerler görünür.
    var consumptionText by remember(vehicleProfile.consumptionPer100Km) {
        mutableStateOf(formatDecimal(vehicleProfile.consumptionPer100Km))
    }
    var capacityText by remember(vehicleProfile.tankCapacityLiters) {
        mutableStateOf(formatDecimal(vehicleProfile.tankCapacityLiters))
    }
    var chargeKwText by remember(vehicleProfile.maxChargeKw) {
        mutableStateOf(formatDecimal(vehicleProfile.maxChargeKw))
    }
    // Çoğu sürücünün dokunmayacağı ayarlar; sayfayı kalabalıklaştırmasın diye kapalı başlar.
    var showAdvanced by remember { mutableStateOf(false) }

    fun commitConsumption() {
        val range = if (isEv) 8.0..60.0 else 2.0..60.0
        val value = parseDecimal(consumptionText)?.coerceIn(range) ?: vehicleProfile.consumptionPer100Km
        consumptionText = formatDecimal(value)
        if (value != vehicleProfile.consumptionPer100Km) onUpdateProfile(vehicleProfile.copy(consumptionPer100Km = value))
    }

    fun commitCapacity() {
        // TIR depoları (çift depoyla 1.500 L'ye kadar) ve büyük bataryalar girilebilsin.
        val range = if (isEv) 10.0..250.0 else 10.0..1500.0
        val value = parseDecimal(capacityText)?.coerceIn(range) ?: vehicleProfile.tankCapacityLiters
        capacityText = formatDecimal(value)
        if (value != vehicleProfile.tankCapacityLiters) onUpdateProfile(vehicleProfile.copy(tankCapacityLiters = value))
    }

    fun commitChargeKw() {
        val value = parseDecimal(chargeKwText)?.coerceIn(20.0, 400.0) ?: vehicleProfile.maxChargeKw
        chargeKwText = formatDecimal(value)
        if (value != vehicleProfile.maxChargeKw) onUpdateProfile(vehicleProfile.copy(maxChargeKw = value))
    }

    fun commitAll() {
        commitConsumption()
        commitCapacity()
        if (isEv) commitChargeKw()
    }

    /** Henüz kaydedilmemiş (odaktaki) sayıların da işlendiği profil; yakıt türü değişirken kaybolmasın. */
    fun withPendingInput(): VehicleProfile {
        val consumptionRange = if (isEv) 8.0..60.0 else 2.0..60.0
        val capacityRange = if (isEv) 10.0..250.0 else 10.0..1500.0
        return vehicleProfile.copy(
            consumptionPer100Km = parseDecimal(consumptionText)?.coerceIn(consumptionRange) ?: vehicleProfile.consumptionPer100Km,
            tankCapacityLiters = parseDecimal(capacityText)?.coerceIn(capacityRange) ?: vehicleProfile.tankCapacityLiters,
            maxChargeKw = if (isEv) parseDecimal(chargeKwText)?.coerceIn(20.0, 400.0) ?: vehicleProfile.maxChargeKw
            else vehicleProfile.maxChargeKw
        )
    }

    // Sayfa aşağı kaydırılarak kapatılınca ModalBottomSheet ilk bestedeki geri çağrıyı kullanır;
    // her zaman güncel profil ve metinlerle kaydetsin diye en son hali tutulur.
    val dismissWithCommit by rememberUpdatedState {
        commitAll()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { dismissWithCommit() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                            onClick = {
                                // Odaktaki sayı önce profile işlenir, sonra tür değişir: tek güncelleme.
                                val pending = withPendingInput()
                                focusManager.clearFocus()
                                onUpdateProfile(pending.copy(fuelType = fuelType))
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, FuelType.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = Accent,
                                activeContentColor = OnAccent,
                                activeBorderColor = Accent,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = TextSecondary,
                                inactiveBorderColor = BorderColor
                            ),
                            icon = {}
                        ) { Text(fuelType.shortName(), maxLines = 1) }
                    }
                }
            }

            if (isEv) {
                EvModelPicker(
                    currentName = vehicleProfile.name,
                    onPick = { preset ->
                        focusManager.clearFocus()
                        onUpdateProfile(
                            vehicleProfile.copy(
                                name = preset?.label ?: VehicleProfile.EV_DEFAULT.name,
                                tankCapacityLiters = preset?.batteryKwh ?: vehicleProfile.tankCapacityLiters,
                                consumptionPer100Km = preset?.consumptionPer100Km ?: vehicleProfile.consumptionPer100Km,
                                maxChargeKw = preset?.maxDcKw ?: vehicleProfile.maxChargeKw
                            )
                        )
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isEv) "Ortalama tüketim (otoyolda)" else "Ortalama tüketim",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                NumericField(
                    value = consumptionText,
                    onValueChange = { if (isDecimalInput(it)) consumptionText = it },
                    onCommit = ::commitConsumption,
                    suffix = if (isEv) "kWh / 100 km" else "L / 100 km"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = if (isEv) listOf(15.0, 17.0, 19.0, 22.0) else listOf(5.5, 6.8, 8.0, 10.0)
                    presets.forEach { preset ->
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
                    if (isEv) {
                        "Kışın ve 120 km/s üstünde tüketim %20-30 artar; uzun yolda buna göre yükselt."
                    } else {
                        "Aracının yol bilgisayarındaki ortalama değeri yazabilirsin."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isEv) "Kullanılabilir batarya" else "Depo hacmi",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                NumericField(
                    value = capacityText,
                    onValueChange = { if (isDecimalInput(it)) capacityText = it },
                    onCommit = ::commitCapacity,
                    suffix = if (isEv) "kWh" else "litre"
                )
            }

            if (isEv) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("En yüksek DC şarj gücü", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    NumericField(
                        value = chargeKwText,
                        onValueChange = { if (isDecimalInput(it)) chargeKwText = it },
                        onCommit = ::commitChargeKw,
                        suffix = "kW"
                    )
                }
                ChoiceGroup(
                    title = "Hızlı şarj soketi",
                    hint = "Türkiye'de satılan araçların neredeyse tamamı CCS2 kullanır.",
                    options = DcConnector.entries,
                    selected = vehicleProfile.connector,
                    label = { it.label },
                    onSelect = { onUpdateProfile(vehicleProfile.copy(connector = it)) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gelişmiş ayarlar", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            if (isEv) {
                                "Şarj durağına en az %${vehicleProfile.reserveThresholdPercent.toInt()}"
                            } else {
                                "Rezerv %${vehicleProfile.reserveThresholdPercent.toInt()} · " +
                                    "Varışta ${vehicleProfile.arrivalFuel.label.lowercase()}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showAdvanced) "Gizle" else "Göster",
                        tint = TextSecondary
                    )
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChoiceGroup(
                            title = if (isEv) "Şarj durağına en az" else "Rezerv payı",
                            hint = if (isEv) {
                                "Şarj istasyonuna bataryada en az bu kadar kalmışken varılır."
                            } else {
                                "Depo bu seviyenin altına inmeden durak planlanır."
                            },
                            options = listOf(10.0, 15.0, 20.0, 25.0),
                            selected = vehicleProfile.reserveThresholdPercent,
                            label = { "%${it.toInt()}" },
                            onSelect = { onUpdateProfile(vehicleProfile.copy(reserveThresholdPercent = it)) }
                        )
                        // Elektrikte varış hedefi planlama ekranındaki kaydırıcıdan seçilir.
                        if (!isEv) {
                            ChoiceGroup(
                                title = "Varışta depoda en az",
                                hint = "Gideceğin yerde istasyon yoksa ya da pahalıysa yükselt; son durakta ona göre yakıt alınır.",
                                options = ArrivalFuel.entries,
                                selected = vehicleProfile.arrivalFuel,
                                label = { it.label },
                                onSelect = { onUpdateProfile(vehicleProfile.copy(arrivalFuel = it)) }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    commitAll()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
            ) { Text("Tamam", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/**
 * Hazır elektrikli araç modelleri: yazdıkça süzülen açılır liste. Model seçilince batarya,
 * tüketim ve şarj gücü doldurulur; "Listede yok" elle girişe bırakır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvModelPicker(
    currentName: String,
    onPick: (EvPreset?) -> Unit
) {
    var query by remember(currentName) { mutableStateOf(currentName) }
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(query) {
        val words = searchKey(query).split(' ').filter(String::isNotBlank)
        EvPresets.all.filter { preset ->
            // Seçili modelin tam adı yazılıyken tüm liste görünsün.
            query == currentName || words.all { it in searchKey(preset.label) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Araç modeli", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    expanded = true
                },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                singleLine = true,
                placeholder = { Text("Marka ya da model yaz", color = TextMuted) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceMuted,
                    unfocusedContainerColor = SurfaceMuted,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Accent
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = CardSurface
            ) {
                matches.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(preset.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    "${formatDecimal(preset.batteryKwh)} kWh · ${formatDecimal(preset.consumptionPer100Km)} kWh/100 km · " +
                                        "${formatDecimal(preset.maxDcKw)} kW",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            }
                        },
                        onClick = {
                            query = preset.label
                            expanded = false
                            onPick(preset)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Listede yok (değerleri elle gir)", color = Accent) },
                    onClick = {
                        expanded = false
                        onPick(null)
                    }
                )
            }
        }
        Text(
            "Araç verileri: Open EV Data (MIT lisansı) ve üretici sayfaları. Değerleri değiştirebilirsin.",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted
        )
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
        containerColor = CardSurface
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
                    Text("Temizle", color = if (selectedBrands.isEmpty()) TextMuted else Accent)
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
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
            ) { Text("Tamam", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/** Şarj ağı tercihi: üyeliği olan ağların istasyonları planda öne alınır (filtre değil). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OperatorSheet(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CardSurface
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
                    Text("Şarj ağı tercihi", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Text(
                        "Üyeliğin olan ağları seç; plan önce onların istasyonlarında durur. " +
                            "Menzilde yoksa yolda kalmaman için başka ağ önerilir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                TextButton(onClick = onClear, enabled = selected.isNotEmpty()) {
                    Text("Temizle", color = if (selected.isEmpty()) TextMuted else Accent)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KNOWN_OPERATORS.forEach { name ->
                    val isSelected = name in selected
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggle(name) },
                        label = { Text(name) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null,
                        colors = chipColors(),
                        border = null
                    )
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
            ) { Text("Tamam", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/** Başlık, tek seçimli çipler ve ne işe yaradığını anlatan kısa not. */
@Composable
private fun <T> ChoiceGroup(
    title: String,
    hint: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                    colors = chipColors(),
                    border = null
                )
            }
        }
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}

@Composable
internal fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = SurfaceMuted,
    labelColor = TextPrimary,
    selectedContainerColor = Accent,
    selectedLabelColor = OnAccent,
    selectedLeadingIconColor = OnAccent
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
            focusedContainerColor = SurfaceMuted,
            unfocusedContainerColor = SurfaceMuted,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = Accent
        )
    )
}

private fun isDecimalInput(value: String): Boolean =
    value.length <= 7 && value.matches(Regex("^\\d{0,4}([.,]\\d{0,2})?$"))

private fun parseDecimal(value: String): Double? = value.replace(',', '.').toDoubleOrNull()

/**
 * Arama için büyük/küçük harf ve Türkçe i/ı farkını yok sayar: "id.4" yazan kullanıcı "ID.4"ü,
 * "togg" yazan "TOGG"u bulur (Türkçe yerel ayarla "I" -> "ı" olduğu için düz lowercase yetmez).
 */
internal fun searchKey(text: String): String = text
    .replace('İ', 'i').replace('I', 'i').replace('ı', 'i')
    .lowercase(java.util.Locale.ROOT)
    .replace("\u0307", "")
    .trim()
