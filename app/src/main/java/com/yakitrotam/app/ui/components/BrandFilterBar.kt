package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.ui.theme.*

@Composable
fun BrandFilterBar(
    selectedBrands: Set<FuelBrand>,
    onToggleBrand: (FuelBrand) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val supportedBrands = listOf(
        FuelBrand.SHELL,
        FuelBrand.OPET,
        FuelBrand.PETROL_OFISI,
        FuelBrand.BP,
        FuelBrand.TOTAL,
        FuelBrand.AYTEMIZ,
        FuelBrand.TP
    )

    Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "İstasyon tercihleri",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            TextButton(onClick = onSelectAll) {
                Text(
                    text = if (selectedBrands.isEmpty()) "Tümü Seçili" else "Tümünü Seç",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryBlue
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(supportedBrands) { brand ->
                val isSelected = selectedBrands.contains(brand)
                val brandPrimaryColor = Color(brand.primaryColorHex)
                val brandAccentColor = Color(brand.accentColorHex)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) PrimaryBlue.copy(alpha = 0.12f) else DarkSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) PrimaryBlue else DarkBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onToggleBrand(brand) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Marka logo/renk rozeti
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brandPrimaryColor)
                                .border(1.dp, brandAccentColor, RoundedCornerShape(4.dp))
                        )

                        Text(
                            text = brand.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            color = if (isSelected) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        }
    }
}
