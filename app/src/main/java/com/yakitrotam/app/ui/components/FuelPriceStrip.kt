package com.yakitrotam.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.FuelPriceSnapshot
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.FuelAmber
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary

/**
 * Güncel pompa fiyatları. Bilgi amaçlı olduğu için kart değil, sade bir satırdır;
 * kullanıcının yakıt türü öne çıkar, tahmini olan fiyat işaretlenir. Sağdaki zil,
 * fiyat değişince bildirim almayı açıp kapatır.
 */
@Composable
fun FuelPriceStrip(
    snapshot: FuelPriceSnapshot?,
    selectedFuelType: FuelType,
    priceAlertsEnabled: Boolean,
    onTogglePriceAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    snapshot == null -> "Güncel pompa fiyatları alınıyor..."
                    snapshot.isFallback -> "Pompa fiyatları (çevrimdışı, son bilinen)"
                    else -> "Pompa fiyatları · ${snapshot.regionName}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val alertColor = if (priceAlertsEnabled) AccentLime else TextSecondary
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTogglePriceAlerts)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (priceAlertsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = alertColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    if (priceAlertsEnabled) "Bildirim açık" else "Değişince haber ver",
                    style = MaterialTheme.typography.labelMedium,
                    color = alertColor
                )
            }
        }
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
