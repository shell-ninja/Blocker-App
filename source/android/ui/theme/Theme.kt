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
    primary = CyberAccent,
    onPrimary = CyberText,
    secondary = CyberOk,
    onSecondary = CyberText,
    tertiary = CyberWarn,
    background = CyberBg,
    onBackground = CyberText,
    surface = CyberCard,
    onSurface = CyberText,
    surfaceVariant = CyberCardSurface,
    onSurfaceVariant = CyberTextSub,
    outline = CyberCardBorder
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
                window.statusBarColor = CyberBg.toArgb()
                window.navigationBarColor = CyberBg.toArgb()
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
