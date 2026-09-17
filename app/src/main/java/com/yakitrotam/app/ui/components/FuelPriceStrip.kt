package com.yakitrotam.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.FuelPriceSnapshot
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.ui.theme.FuelAmber
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary

/**
 * Güncel pompa fiyatları. Bilgi amaçlı olduğu için kart değil, sade bir satırdır;
 * kullanıcının yakıt türü öne çıkar, tahmini olan fiyat (LPG) işaretlenir.
 */
@Composable
fun FuelPriceStrip(
    snapshot: FuelPriceSnapshot?,
    selectedFuelType: FuelType,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = when {
                snapshot == null -> "Güncel pompa fiyatları alınıyor..."
                snapshot.isFallback -> "Pompa fiyatları (çevrimdışı, son bilinen)"
                else -> "Güncel pompa fiyatları · " +
                    snapshot.regionName.lowercase().split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
            },
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FuelType.entries.forEach { fuelType ->
                val isSelected = fuelType == selectedFuelType
                val estimated = snapshot?.isEstimated(fuelType) == true
                Column {
                    Text(
                        fuelType.shortName() + if (estimated) " (tahmini)" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (estimated) FuelAmber else TextMuted
                    )
                    Text(
                        snapshot?.let { formatPrice(it.priceFor(fuelType)) } ?: "—",
                        style = if (isSelected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        color = if (isSelected) TextPrimary else TextSecondary
                    )
                }
            }
        }
    }
}
