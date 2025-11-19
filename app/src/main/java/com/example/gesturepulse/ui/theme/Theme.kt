package com.example.gesturepulse.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NeonColorScheme = darkColorScheme(
    primary = NeonOrange,
    onPrimary = Color.Black,
    primaryContainer = NeonOrangeDim,
    onPrimaryContainer = TextWhite,

    secondary = NeonBlue,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004D57),

    tertiary = NeonGreen,
    onTertiary = Color.Black,

    background = BlackBackground,
    onBackground = TextWhite,

    surface = DarkSurface,
    onSurface = TextWhite,

    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextGrey,

    error = NeonRed,
    onError = Color.Black
)

@Composable
fun GesturePulseTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = NeonColorScheme

    // Obsługa paska statusu
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Pasek statusu w kolorze tła (czarny)
            window.statusBarColor = colorScheme.background.toArgb()
            // Białe ikonki na pasku statusu
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}