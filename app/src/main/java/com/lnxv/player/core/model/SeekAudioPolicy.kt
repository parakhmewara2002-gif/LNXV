package com.lnxv.player.core.model

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

enum class SeekMode { CLOSEST_SYNC, EXACT }

/** Fast keyframe seeks while the user drags the seek bar, exact seek once the finger is released. */
object SeekPolicy {
    fun modeFor(scrubbing: Boolean, exactSeekEnabled: Boolean): SeekMode =
        if (scrubbing || !exactSeekEnabled) SeekMode.CLOSEST_SYNC else SeekMode.EXACT
}

/** Volume boost expressed as a percentage (100% = no boost) and converted to LoudnessEnhancer millibels. */
object AudioBoost {
    const val MAX_PERCENT = 200
    const val WARN_ABOVE_PERCENT = 150
    val STEPS: List<Int> = listOf(100, 125, 150, 175, 200)

    fun percentToMb(percent: Int): Int {
        val p = percent.coerceIn(100, MAX_PERCENT)
        return if (p == 100) 0 else (2000.0 * log10(p / 100.0)).roundToInt()
    }

    fun mbToPercent(mb: Int): Int =
        if (mb <= 0) 100 else (100.0 * 10.0.pow(mb / 2000.0)).roundToInt().coerceIn(100, MAX_PERCENT)

    /** Next step in [STEPS]; wraps back to 100% after the maximum. */
    fun next(percent: Int): Int = STEPS.firstOrNull { it > percent } ?: STEPS.first()

    fun needsWarning(percent: Int): Boolean = percent > WARN_ABOVE_PERCENT
}
