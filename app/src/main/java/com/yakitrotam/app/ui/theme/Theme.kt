package com.yakitrotam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentSoft,
    onPrimaryContainer = AccentDim,
    secondary = FuelAmber,
    onSecondary = OnAccent,
    tertiary = AccentViolet,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondary,
    error = ReserveRed,
    onError = OnAccent,
    outline = BorderColor,
    outlineVariant = BorderColor,
    surfaceContainer = CardSurface,
    surfaceContainerLow = CardSurface,
    surfaceContainerHigh = ElevatedSurface
)

/**
 * Uygulama tek temalıdır: açık nane teması. Sistemin karanlık moduna uymaz.
 * ponytail: renkler bileşenlere doğrudan bağlı; karanlık mod istenirse MaterialTheme.colorScheme'e taşınmalı.
 */
@Composable
fun YakitRotamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
