package com.lnxv.player.multiscreen

/** Pure follower-sync maths (clock offset, target position, correction choice). */
object SyncMath {
    sealed interface Action {
        object None : Action
        data class Seek(val toMs: Long) : Action
        data class Speed(val factor: Float) : Action
    }

    const val SEEK_THRESHOLD_MS = 250L
    const val NUDGE_THRESHOLD_MS = 40L
    const val NUDGE = 0.03f

    /** Estimated host clock offset: hostClock = clientClock + offset. [c0]/[c1] = client send/receive time, [h] = host time at reply. */
    fun clockOffset(c0: Long, c1: Long, h: Long): Long = h - (c0 + c1) / 2

    fun rtt(c0: Long, c1: Long): Long = c1 - c0

    /** Where the host is now, extrapolated from its last report. */
    fun targetPosition(hostPosMs: Long, hostClockAtReportMs: Long, hostClockNowMs: Long, playing: Boolean, speed: Float): Long {
        if (!playing) return hostPosMs
        val elapsed = (hostClockNowMs - hostClockAtReportMs).coerceAtLeast(0L)
        return hostPosMs + (elapsed * speed).toLong()
    }

    /** drift = followerPosition - targetPosition. Positive = follower is ahead. */
    fun decide(driftMs: Long, baseSpeed: Float, seekAllowed: Boolean): Action {
        val abs = Math.abs(driftMs)
        return when {
            abs > SEEK_THRESHOLD_MS && seekAllowed -> Action.Seek(0L) // caller substitutes the target position
            abs > NUDGE_THRESHOLD_MS -> Action.Speed(if (driftMs > 0) baseSpeed * (1f - NUDGE) else baseSpeed * (1f + NUDGE))
            else -> Action.Speed(baseSpeed)
        }
    }
}
