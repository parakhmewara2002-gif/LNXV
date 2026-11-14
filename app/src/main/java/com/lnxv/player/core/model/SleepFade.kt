package com.lnxv.player.core.model

/** Pure helpers for the sleep-timer fade-out (Phase 5). */
object SleepFade {
    const val END_OF_VIDEO = -1
    const val FADE_MS = 10_000L

    /** Volume multiplier 1.0 -> 0.0 over the last [fadeMs] before the timer fires. */
    fun factor(remainingMs: Long, fadeMs: Long = FADE_MS): Float {
        if (fadeMs <= 0L || remainingMs >= fadeMs) return 1f
        if (remainingMs <= 0L) return 0f
        return remainingMs.toFloat() / fadeMs.toFloat()
    }
}
