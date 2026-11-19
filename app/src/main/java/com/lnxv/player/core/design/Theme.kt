package com.lnxv.player.core.design

import android.app.Activity
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.LocalHazeStyle
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
    glassProfile: GlassProfile = if (reduceEffects) GlassProfile.LOW else GlassProfile.BALANCED,
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

    // Haze 1.0.x throws "backgroundColor not specified" while drawing any hazeChild without an explicit style,
    // which crashed the app on launch. Provide one default style for every hazeChild in the app.
    val hazeStyle = HazeStyle(
        backgroundColor = palette.background,
        tints = emptyList(),
        blurRadius = 24.dp,
        noiseFactor = 0.04f
    )
    CompositionLocalProvider(
        LocalLNXVColors provides palette,
        LocalReduceEffects provides reduceEffects,
        LocalGlassProfile provides glassProfile,
        LocalHazeStyle provides hazeStyle
    ) {
        MaterialTheme(
            colorScheme = colorScheme.withGlassContainers(darkTheme),
            typography = Typography,
            shapes = IosShapes,
            content = content
        )
    }
}

/** iOS-like rounded shapes: dialogs use extraLarge, menus/sheets use large/medium. */
private val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

/** Frosted dialog / menu / sheet surfaces (AlertDialog, DropdownMenu, ModalBottomSheet read the surfaceContainer roles). */
private fun androidx.compose.material3.ColorScheme.withGlassContainers(dark: Boolean) = copy(
    surfaceContainerLow = if (dark) Color(0xF01B1F26) else Color(0xF2F4F5F8),
    surfaceContainer = if (dark) Color(0xF2222730) else Color(0xF2FFFFFF),
    surfaceContainerHigh = if (dark) Color(0xF2272C36) else Color(0xF2FFFFFF),
    surfaceContainerHighest = if (dark) Color(0xF22D333E) else Color(0xF2F2F3F7)
)

/** The video player is always dark (like most players), whatever theme the rest of the app uses. */
@Composable
fun LNXVPlayerTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLNXVColors provides AmoledPalette,
        LocalHazeStyle provides HazeStyle(backgroundColor = Color.Black, tints = emptyList(), blurRadius = 24.dp, noiseFactor = 0.04f)
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme.copy(background = Color.Black).withGlassContainers(true),
            typography = Typography,
            shapes = IosShapes,
            content = content
        )
    }
}
