package com.lnxv.player.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Theme-aware colours for the LNXV "glass" UI (light, dark and AMOLED black). */
@Immutable
data class LNXVColors(
    val isDark: Boolean,
    val background: Color,
    val hasGlow: Boolean,
    val glowA: Color,
    val glowB: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val glassSubtle: Color,
    val barFill: Color,
    val placeholder: Color,
    val accent: Color
)

/** True black: no glow, pure #000000 background. */
val AmoledPalette = LNXVColors(
    isDark = true,
    background = AmoledBlack,
    hasGlow = false,
    glowA = Color.Transparent,
    glowB = Color.Transparent,
    textPrimary = Color.White,
    textSecondary = Color(0x99FFFFFF),
    textTertiary = Color(0x80FFFFFF),
    glassFill = Color(0x261E293B),
    glassBorder = Color(0x26FFFFFF),
    glassSubtle = Color(0x14FFFFFF),
    barFill = Color(0xCC000000),
    placeholder = Color(0xFF0F141C),
    accent = NeonCyan
)

/** Dark (not pure black): soft charcoal with a faint colour glow so the glass has something to refract. */
val DarkPalette = AmoledPalette.copy(
    background = Color(0xFF14181F),
    hasGlow = true,
    glowA = NeonCyan.copy(alpha = 0.14f),
    glowB = ElectricViolet.copy(alpha = 0.16f),
    barFill = Color(0xCC14181F),
    glassFill = Color(0x331E293B)
)

val LightPalette = LNXVColors(
    isDark = false,
    background = Color(0xFFF2F5FA),
    hasGlow = true,
    glowA = Color(0xFF00B8D4).copy(alpha = 0.20f),
    glowB = Color(0xFF7C4DFF).copy(alpha = 0.14f),
    textPrimary = Color(0xFF14181F),
    textSecondary = Color(0xFF566070),
    textTertiary = Color(0xFF8792A2),
    glassFill = Color(0xB3FFFFFF),
    glassBorder = Color(0x24000000),
    glassSubtle = Color(0x0F000000),
    barFill = Color(0xE6F2F5FA),
    placeholder = Color(0xFFDDE3EB),
    accent = Color(0xFF00838F)
)

val LocalLNXVColors = staticCompositionLocalOf { AmoledPalette }

val appColors: LNXVColors
    @Composable
    @ReadOnlyComposable
    get() = LocalLNXVColors.current

/** Opaque screen background: solid colour plus (for light/dark, not AMOLED) two soft colour glows. */
@Composable
fun Modifier.ambientBackground(): Modifier {
    val c = appColors
    return this
        .background(c.background)
        .drawBehind {
            if (c.hasGlow) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(c.glowA, Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.04f),
                        radius = size.maxDimension * 0.55f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(c.glowB, Color.Transparent),
                        center = Offset(size.width * 0.02f, size.height * 0.98f),
                        radius = size.maxDimension * 0.5f
                    )
                )
            }
        }
}
