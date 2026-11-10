package com.lnxv.player.core.design

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val AmoledBlack = Color(0xFF000000)
val DarkBackground = Color(0xFF080B10)
val SurfaceDark = Color(0xFF101622)
val SurfaceDarkElevated = Color(0xFF182030)

// Liquid Glass Palette
val GlassWhiteLight = Color(0x33FFFFFF)
val GlassWhiteSubtle = Color(0x1AFFFFFF)
val GlassWhiteUltraSubtle = Color(0x0DFFFFFF)
val GlassBorder = Color(0x40FFFFFF)
val GlassBorderSubtle = Color(0x26FFFFFF)
val GlassShadow = Color(0x40000000)

// Neon Accents
val NeonCyan = Color(0xFF00E5FF)
val ElectricViolet = Color(0xFF7C4DFF)
val CrimsonAlert = Color(0xFFFF3366)
val SuccessEmerald = Color(0xFF00E676)
val AmberWarning = Color(0xFFFFAB00)

// Light Theme Palette
val LightBackground = Color(0xFFF5F7FA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceLightElevated = Color(0xFFEBF0F5)
val GlassDarkLight = Color(0x14000000)
val GlassBorderLight = Color(0x26000000)

// Gradients
val GlassHeaderGradient = Brush.verticalGradient(
    colors = listOf(Color(0xCC080B10), Color(0x00080B10))
)

val GlassFooterGradient = Brush.verticalGradient(
    colors = listOf(Color(0x00080B10), Color(0xEE080B10))
)

val AccentGradient = Brush.horizontalGradient(
    colors = listOf(NeonCyan, ElectricViolet)
)
