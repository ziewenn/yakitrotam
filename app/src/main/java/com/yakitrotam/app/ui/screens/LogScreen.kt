package com.yakitrotam.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.ads.NativeAdCard
import com.yakitrotam.app.data.model.FuelLog
import com.yakitrotam.app.data.model.LogEntry
import com.yakitrotam.app.data.model.VehicleProfile
import com.yakitrotam.app.ui.components.SurfaceCard
import com.yakitrotam.app.ui.components.formatDecimal
import com.yakitrotam.app.ui.components.formatMoney
import com.yakitrotam.app.ui.components.formatPrice
import com.yakitrotam.app.ui.theme.Accent
import com.yakitrotam.app.ui.theme.AppBackground
import com.yakitrotam.app.ui.theme.OnAccent
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Yakıt ve şarj defteri: dolumlar girilir, iki tam dolum arasından gerçek tüketim hesaplanır
 * ve tek dokunuşla araca uygulanır. Elektrikli araçta yalnızca şarj kayıtları sayılır.
 */
@Composable
fun LogScreen(
    vehicleProfile: VehicleProfile,
    entries: List<LogEntry>,
    onAdd: (LogEntry) -> Unit,
    onRemove: (Long) -> Unit,
    onApplyConsumption: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val electric = vehicleProfile.isElectric
    val unit = vehicleProfile.fuelType.unit
    val mine = remember(entries, electric) {
        entries.filter { it.electric == electric }.sortedByDescending { it.odometerKm }
    }
    val stats = remember(entries, electric) { FuelLog.stats(entries, electric) }
    var showAdd by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    if (electric) "Şarj defteri" else "Yakıt defteri",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            item {
                SurfaceCard {
                    val measured = stats.consumptionPer100Km
                    if (measured == null) {
                        Text("Gerçek tüketimin", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            if (electric) "Her şarjda kilometreyi ve aldığın kWh'i gir. Bataryayı aynı yüzdeye " +
                                "doldurduğun iki şarj arasından gerçek tüketimin hesaplanır."
                            else "Her dolumda kilometreyi ve aldığın litreyi gir. İki tam dolum arasından " +
                                "aracının gerçek tüketimi hesaplanır.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        Text("Gerçek tüketimin", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Text(
                            "${formatDecimal(measured)} $unit/100 km",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            "${formatDecimal(stats.measuredKm)} km yoldan ölçüldü · araçta " +
                                "${formatDecimal(vehicleProfile.consumptionPer100Km)} $unit/100 km kayıtlı",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        stats.costPer100KmTL?.let {
                            Text("100 km maliyeti: ${formatPrice(it)}", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                        val rounded = Math.round(measured * 10) / 10.0
                        if (rounded != vehicleProfile.consumptionPer100Km) {
                            Button(
                                onClick = { onApplyConsumption(measured) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
                            ) { Text("Planlarda bunu kullan") }
                        }
                    }
                    Text(
                        "Bu ay: ${formatMoney(stats.thisMonthSpendTL)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
            }
            // Gelir: planlayıcı ve haritadaki banner'lara ek olarak defterde de içerik arası reklam.
            item { NativeAdCard() }
            items(mine, key = { it.id }) { entry -> EntryRow(entry, unit) { onRemove(entry.id) } }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            containerColor = Accent,
            contentColor = OnAccent,
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text(if (electric) "Şarj ekle" else "Dolum ekle") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }

    if (showAdd) {
        AddEntryDialog(
            electric = electric,
            unit = unit,
            lastOdometer = mine.firstOrNull()?.odometerKm,
            onDismiss = { showAdd = false },
            onSave = {
                onAdd(it)
                showAdd = false
            }
        )
    }
}

private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("tr"))

@Composable
private fun EntryRow(entry: LogEntry, unit: String, onDelete: () -> Unit) {
    SurfaceCard(padding = 12.dp, spacing = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    "${formatDecimal(entry.amount)} $unit · ${formatMoney(entry.totalPriceTL)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    listOfNotNull(
                        dateFormat.format(Date(entry.dateMillis)),
                        "${formatDecimal(entry.odometerKm)} km",
                        "${formatPrice(entry.unitPriceTL)}/$unit",
                        if (entry.fullFill) null else "kısmi"
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Sil", tint = TextMuted) }
        }
    }
}

@Composable
private fun AddEntryDialog(
    electric: Boolean,
    unit: String,
    lastOdometer: Double?,
    onDismiss: () -> Unit,
    onSave: (LogEntry) -> Unit
) {
    var odometer by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(true) }
    fun parse(text: String) = text.replace(".", "").replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    // Kilometre "123.456" gibi binlik noktayla da yazılabilir; litre ve tutarda virgül ondalıktır.
    fun parseAmount(text: String) = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val km = parse(odometer)
    val amt = parseAmount(amount)
    val price = parseAmount(total)
    val kmError = km != null && lastOdometer != null && km <= lastOdometer
    val valid = km != null && amt != null && price != null && !kmError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (electric) "Şarj ekle" else "Dolum ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.take(9) },
                    label = { Text("Kilometre sayacı") },
                    supportingText = when {
                        kmError -> { { Text("Son kayıttan (${formatDecimal(lastOdometer!!)} km) büyük olmalı") } }
                        lastOdometer != null -> { { Text("Son kayıt: ${formatDecimal(lastOdometer)} km") } }
                        else -> null
                    },
                    isError = kmError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.take(7) },
                    label = { Text(if (electric) "Alınan enerji ($unit)" else "Alınan yakıt ($unit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it.take(8) },
                    label = { Text("Ödenen tutar (₺)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = full,
                        onCheckedChange = { full = it },
                        colors = CheckboxDefaults.colors(checkedColor = Accent)
                    )
                    Text(
                        if (electric) "Her zamanki yüzdeye kadar şarj ettim" else "Depoyu tam doldurdum",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(LogEntry(now, now, km!!, amt!!, price!!, full, electric))
                }
            ) { Text("Kaydet", color = if (valid) Accent else TextMuted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = TextSecondary) } }
    )
}
