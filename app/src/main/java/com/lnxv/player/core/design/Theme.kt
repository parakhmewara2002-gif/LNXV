package com.lnxv.player.core.design

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    secondary = ElectricViolet,
    onSecondary = Color.White,
    background = AmoledBlack,
    onBackground = Color(0xFFF0F6FC),
    surface = SurfaceDark,
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = Color(0xFF8B949E),
    error = CrimsonAlert,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    secondary = ElectricViolet,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1C1C1E),
    surface = SurfaceLight,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = SurfaceLightElevated,
    onSurfaceVariant = Color(0xFF636366),
    error = CrimsonAlert,
    onError = Color.White
)

@Composable
fun LNXVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> {
            if (amoled) DarkColorScheme.copy(background = AmoledBlack)
            else DarkColorScheme.copy(background = DarkBackground)
        }
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
