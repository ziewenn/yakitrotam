package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.FuelPriceSnapshot
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurface
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.FuelAmber
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary

/**
 * Güncel pompa fiyatları şeridi. Seçili yakıt türü vurgulanır,
 * tahmini olan fiyatlar (LPG) ayrıca işaretlenir.
 */
@Composable
fun FuelPriceStrip(
    snapshot: FuelPriceSnapshot?,
    selectedFuelType: FuelType,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(
                    modifier = Modifier.size(7.dp).clip(CircleShape)
                        .background(if (snapshot?.isFallback == false) AccentLime else FuelAmber)
                )
                Text(
                    text = "GÜNCEL POMPA FİYATI",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
            when {
                snapshot == null -> CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    color = AccentLime,
                    strokeWidth = 1.5.dp
                )
                else -> Text(
                    text = snapshot.regionName.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FuelType.entries.forEach { fuelType ->
                val isSelected = fuelType == selectedFuelType
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (isSelected) AccentLime.copy(alpha = 0.13f) else DarkSurfaceVariant)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) AccentLime.copy(alpha = 0.6f) else DarkBorder,
                            shape = RoundedCornerShape(13.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = when (fuelType) {
                            FuelType.BENZIN -> "BENZİN"
                            FuelType.DIZEL -> "MOTORİN"
                            FuelType.LPG -> "LPG"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) AccentLime else TextMuted
                    )
                    Text(
                        text = snapshot?.let { formatPrice(it.priceFor(fuelType)) } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = if (snapshot?.isEstimated(fuelType) == true) "tahmini" else "canlı",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (snapshot?.isEstimated(fuelType) == true) FuelAmber else TextMuted
                    )
                }
            }
        }

        if (snapshot != null) {
            Text(
                text = "Kaynak: ${snapshot.sourceName} · ${snapshot.priceDate}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}
