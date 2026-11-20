package com.lnxv.player.core.design

import androidx.compose.ui.unit.dp

/** Spacing scale (4 dp grid). New UI uses these instead of ad-hoc dp literals; existing screens migrate gradually. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radii. */
object Radius {
    val chip = 12.dp
    val card = 20.dp
    val sheet = 28.dp
}

/** Accessibility sizes. 48 dp is the Android minimum touch target. */
object Touch {
    val minTarget = 48.dp
}

/** Motion durations in milliseconds and the reduced-motion rule. */
object MotionTokens {
    const val FAST_MS = 120
    const val STANDARD_MS = 220
    const val SLOW_MS = 360

    /**
     * Applies the system "animator duration scale" (Settings > Developer options, or Remove animations in
     * accessibility): 0 means animations are off, so the duration collapses to 0; otherwise it is scaled and clamped
     * to at least 1 ms.
     */
    fun scaled(baseMs: Int, animatorScale: Float): Int {
        if (animatorScale <= 0f || animatorScale.isNaN()) return 0
        return (baseMs * animatorScale).toInt().coerceAtLeast(1)
    }
}
