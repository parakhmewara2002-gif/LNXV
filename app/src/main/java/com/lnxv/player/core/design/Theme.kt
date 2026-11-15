package com.lnxv.player.core.design

import android.app.Activity
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    dynamicColor: Boolean = false,
    reduceEffects: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamic -> {
            val dyn = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && amoled) dyn.copy(background = AmoledBlack) else dyn
        }
        darkTheme -> {
            if (amoled) DarkColorScheme.copy(background = AmoledBlack)
            else DarkColorScheme.copy(background = Color(0xFF16191F), surface = Color(0xFF1F232B))
        }
        else -> LightColorScheme.copy(primary = LightPalette.accent, background = LightPalette.background)
    }
    val basePalette = when {
        !darkTheme -> LightPalette
        amoled -> AmoledPalette
        else -> DarkPalette
    }
    val palette = if (useDynamic) basePalette.copy(accent = colorScheme.primary) else basePalette

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

    CompositionLocalProvider(LocalLNXVColors provides palette, LocalReduceEffects provides reduceEffects) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/** The video player is always dark (like most players), whatever theme the rest of the app uses. */
@Composable
fun LNXVPlayerTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLNXVColors provides AmoledPalette) {
        MaterialTheme(
            colorScheme = DarkColorScheme.copy(background = Color.Black),
            typography = Typography,
            content = content
        )
    }
}
