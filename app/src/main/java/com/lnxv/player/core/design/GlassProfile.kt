package com.lnxv.player.core.design

import androidx.compose.runtime.compositionLocalOf

/**
 * Rendering tier for glass surfaces.
 *  LOW      translucent fill + hairline border + top highlight (minimum draw work, no blur).
 *  BALANCED + inner depth shadow, two-lobe specular edge, soft inner highlight lobes, Haze blur on the few bars.
 *  HIGH     + static chromatic edge fringe and, on API 33+, an AGSL lens (real refraction + frost of the list behind the bars).
 */
enum class GlassProfile {
    LOW, BALANCED, HIGH;

    val layeredHighlights: Boolean get() = this != LOW
    val allowsLens: Boolean get() = this == HIGH
}

val LocalGlassProfile = compositionLocalOf { GlassProfile.BALANCED }

/** Pure (JVM-testable) tier selection from real device facts and the user's Settings override (EffectsMode). */
object GlassProfileResolver {
    const val HIGH_RAM_BYTES = 7_500_000_000L
    const val LENS_MIN_API = 33
    const val HIGH_MIN_REFRESH_HZ = 89f

    fun resolve(
        mode: EffectsMode,
        lowEnd: Boolean,
        apiLevel: Int,
        totalRamBytes: Long,
        maxRefreshHz: Float,
        systemAnimationsOff: Boolean
    ): GlassProfile {
        if (systemAnimationsOff) return GlassProfile.LOW
        return when (mode) {
            // Settings > Effects "On" means "reduce effects": the cheapest tier (still styled, never flat).
            EffectsMode.ON -> GlassProfile.LOW
            // "Off" = do not reduce anything: force the richest tier this OS can render.
            EffectsMode.OFF -> if (apiLevel >= LENS_MIN_API) GlassProfile.HIGH else GlassProfile.BALANCED
            EffectsMode.AUTO -> when {
                lowEnd -> GlassProfile.LOW
                apiLevel >= LENS_MIN_API && totalRamBytes >= HIGH_RAM_BYTES && maxRefreshHz >= HIGH_MIN_REFRESH_HZ -> GlassProfile.HIGH
                else -> GlassProfile.BALANCED
            }
        }
    }
}
