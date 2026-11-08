package com.blocker.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NordicColors.AccentPrimary,
    onPrimary = NordicColors.OnAccentPrimary,
    secondary = NordicColors.AccentPrimary,
    onSecondary = NordicColors.OnAccentPrimary,
    tertiary = NordicColors.SuccessMuted,
    background = NordicColors.CanvasBg,
    onBackground = NordicColors.TextPrimary,
    surface = NordicColors.CardSurface,
    onSurface = NordicColors.TextPrimary,
    surfaceVariant = NordicColors.CardSurfaceHero,
    onSurfaceVariant = NordicColors.TextSecondary,
    outline = NordicColors.HairlineBorder
)

@Composable
fun BlockerTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = NordicColors.CanvasBg.toArgb()
                window.navigationBarColor = NordicColors.CanvasBg.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
