package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.FuelBrand
import com.yakitrotam.app.ui.theme.DarkSurface
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import java.util.Locale

val CardShape = RoundedCornerShape(16.dp)

/** Uygulamadaki tüm blokların ortak kabı: kenarlıksız, zeminden bir ton açık yüzey. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(DarkSurface)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/** Simge + başlık + özet + ok: dokununca ayrıntı sayfası açan ayar satırı. */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
    }
}

/** Marka renginde, markanın baş harflerini taşıyan rozet. */
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
            .clip(RoundedCornerShape((size / 3.4f).dp))
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

private val turkish: Locale = Locale.forLanguageTag("tr")

internal fun formatDecimal(value: Double): String =
    String.format(turkish, if (value % 1.0 == 0.0) "%.0f" else "%.1f", value)

/** 12.480 ₺ */
internal fun formatMoney(value: Double): String = String.format(turkish, "%,.0f ₺", value)

/** 80,16 ₺ */
internal fun formatPrice(value: Double): String = String.format(turkish, "%.2f ₺", value)

internal fun formatDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours sa $minutes dk" else "$minutes dk"
}
