package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurface
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary

/** Uygulamadaki tüm bölümlerin ortak kabı: aynı köşe yarıçapı, kenarlık ve dolgu. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentPadding: Int = 18,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(22.dp))
            .padding(contentPadding.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (title != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(AccentLime.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = AccentLime, modifier = Modifier.size(17.dp))
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                trailing?.invoke()
            }
        }
        content()
    }
}

/** Büyük sayı + küçük etiket. Özet ekranındaki metrik ızgarasının yapı taşı. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    caption: String? = null,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = valueColor,
            maxLines = 1
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                maxLines = 1
            )
        }
    }
}

/** Marka renginde, markanın baş harflerini taşıyan kare rozet. */
@Composable
fun BrandBadge(
    brand: FuelBrand,
    modifier: Modifier = Modifier,
    size: Int = 36
) {
    val brandColor = Color(brand.primaryColorHex)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(brandColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = brand.initials(),
            style = MaterialTheme.typography.labelLarge,
            color = brandColor.readableForeground()
        )
    }
}

fun FuelBrand.initials(): String = when (this) {
    FuelBrand.PETROL_OFISI -> "PO"
    FuelBrand.TP -> "TP"
    FuelBrand.BP -> "BP"
    FuelBrand.DIGER -> "?"
    else -> displayName.take(2).uppercase()
}

/** Açık zeminde siyah, koyu zeminde beyaz yazı: marka rozetleri her zaman okunur kalsın. */
fun Color.readableForeground(): Color =
    if (red * 0.299f + green * 0.587f + blue * 0.114f > 0.6f) Color.Black else Color.White
