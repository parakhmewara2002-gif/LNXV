package com.lnxv.player.core.security

/**
 * Persisted lockout state: after [FREE_ATTEMPTS] wrong PINs every further failure
 * locks the vault for an exponentially growing delay (30s, 1m, 5m, 15m, 1h cap).
 * Uses wall-clock time so it survives restarts; a user changing the system clock
 * can shorten it (documented limitation).
 */
data class LockoutState(val failedAttempts: Int = 0, val lockedUntilMs: Long = 0L)

object LockoutPolicy {
    const val FREE_ATTEMPTS = 5
    private val STEPS_MS = longArrayOf(30_000L, 60_000L, 300_000L, 900_000L, 3_600_000L)

    fun delayMsForFailures(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0L
        val idx = (failures - FREE_ATTEMPTS).coerceAtMost(STEPS_MS.size - 1)
        return STEPS_MS[idx]
    }

    fun remainingMs(state: LockoutState, nowMs: Long): Long {
        val remaining = state.lockedUntilMs - nowMs
        // A lock further in the future than the maximum delay means the clock moved back: cap it.
        return remaining.coerceIn(0L, STEPS_MS.last())
    }

    fun isLocked(state: LockoutState, nowMs: Long): Boolean = remainingMs(state, nowMs) > 0L

    fun onFailure(state: LockoutState, nowMs: Long): LockoutState {
        val failures = state.failedAttempts + 1
        val delay = delayMsForFailures(failures)
        return LockoutState(failures, if (delay > 0) nowMs + delay else 0L)
    }

    fun onSuccess(): LockoutState = LockoutState()
}
