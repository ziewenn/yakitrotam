package com.yakitrotam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentLime,
    onPrimary = DarkBackground,
    primaryContainer = AccentLimeDim,
    onPrimaryContainer = DarkBackground,
    secondary = FuelAmber,
    onSecondary = DarkBackground,
    tertiary = AccentViolet,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ReserveRed,
    onError = DarkBackground,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = ElevatedSurface
)

/** Uygulama tek temalıdır: sürüş sırasında okunaklı olan koyu tema. */
@Composable
fun YakitRotamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
