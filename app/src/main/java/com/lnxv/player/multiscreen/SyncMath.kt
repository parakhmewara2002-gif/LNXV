package com.lnxv.player.multiscreen

/** Pure follower-sync maths (clock offset, target position, correction choice). */
object SyncMath {
    sealed interface Action {
        object None : Action
        data class Seek(val toMs: Long) : Action
        data class Speed(val factor: Float) : Action
    }

    const val SEEK_THRESHOLD_MS = 600L
    const val DEAD_ZONE_MS = 25L

    /** Estimated host clock offset: hostClock = clientClock + offset. [c0]/[c1] = client send/receive time, [h] = host time at reply. */
    fun clockOffset(c0: Long, c1: Long, h: Long): Long = h - (c0 + c1) / 2

    fun rtt(c0: Long, c1: Long): Long = c1 - c0

    /** Where the host is now, extrapolated from its last report. */
    fun targetPosition(hostPosMs: Long, hostClockAtReportMs: Long, hostClockNowMs: Long, playing: Boolean, speed: Float): Long {
        if (!playing) return hostPosMs
        val elapsed = (hostClockNowMs - hostClockAtReportMs).coerceAtLeast(0L)
        return hostPosMs + (elapsed * speed).toLong()
    }

    /**
     * Continuous micro-pitch speed matching.
     * drift = followerPosition - targetPosition. Positive = follower is ahead.
     * Smoothly scales speed without acoustic artifacts or visual stutter.
     */
    fun decide(driftMs: Long, baseSpeed: Float, seekAllowed: Boolean): Action {
        val abs = Math.abs(driftMs)
        return when {
            abs > SEEK_THRESHOLD_MS && seekAllowed -> Action.Seek(0L)
            abs > DEAD_ZONE_MS -> {
                // Proportional pitch scaling: smoothly adjust between ±0.8% and ±4.5%
                val factorAdjustment = ((driftMs.toFloat() / 500f) * 0.04f).coerceIn(-0.045f, 0.045f)
                Action.Speed(baseSpeed * (1f - factorAdjustment))
            }
            else -> Action.Speed(baseSpeed)
        }
    }
}
