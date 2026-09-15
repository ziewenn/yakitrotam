package com.yakitrotam.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DarkBackground.toArgb()
                window.navigationBarColor = DarkBackground.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
