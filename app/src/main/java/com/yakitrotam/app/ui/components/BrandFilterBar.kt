package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary

@Composable
fun BrandFilterBar(
    selectedBrands: Set<FuelBrand>,
    onToggleBrand: (FuelBrand) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val supportedBrands = FuelBrand.entries.filter { it != FuelBrand.DIGER }

    SectionCard(
        modifier = modifier,
        title = "Marka tercihi",
        icon = Icons.Default.LocalGasStation,
        trailing = {
            TextButton(onClick = onSelectAll, enabled = selectedBrands.isNotEmpty()) {
                Text(
                    text = if (selectedBrands.isEmpty()) "TÜMÜ" else "SIFIRLA",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedBrands.isEmpty()) TextMuted else AccentLime
                )
            }
        }
    ) {
        Text(
            text = if (selectedBrands.isEmpty()) {
                "Tüm markalar değerlendiriliyor — en az sapan istasyon seçilir."
            } else {
                "${selectedBrands.size} marka önceliklendirildi. Menzil içinde yoksa " +
                    "yolda kalmamanız için başka marka önerilir."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(supportedBrands) { brand ->
                val isSelected = selectedBrands.contains(brand)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) AccentLime.copy(alpha = 0.12f) else DarkSurfaceVariant)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) AccentLime else DarkBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onToggleBrand(brand) }
                        .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        BrandBadge(brand = brand, size = 30)
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(AccentLime),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.Black,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = brand.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) TextPrimary else TextSecondary
                    )
                }
            }
        }
    }
}
