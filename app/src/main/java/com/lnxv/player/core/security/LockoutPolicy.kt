package com.lnxv.player.core.security

/**
 * Persisted lockout state: after [FREE_ATTEMPTS] wrong PINs every further failure
 * locks the vault for an exponentially growing delay (30s, 1m, 5m, 15m, 1h cap).
 *
 * Within one boot the remaining time is measured with the monotonic clock ([lockedUntilElapsedMs] vs
 * SystemClock.elapsedRealtime, tied to [bootCount]), so changing the system clock cannot shorten it.
 * After a reboot (or for states saved by older versions) it falls back to the wall clock, where a clock
 * change can still shorten it (documented limitation).
 */
data class LockoutState(
    val failedAttempts: Int = 0,
    val lockedUntilMs: Long = 0L,
    val lockedUntilElapsedMs: Long = 0L,
    val bootCount: Int = 0
)

/** The three clocks the policy needs. [bootCount] 0 means unknown (monotonic comparison is skipped). */
data class LockoutClock(val wallMs: Long, val elapsedMs: Long = 0L, val bootCount: Int = 0)

object LockoutPolicy {
    const val FREE_ATTEMPTS = 5
    private val STEPS_MS = longArrayOf(30_000L, 60_000L, 300_000L, 900_000L, 3_600_000L)

    fun delayMsForFailures(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0L
        val idx = (failures - FREE_ATTEMPTS).coerceAtMost(STEPS_MS.size - 1)
        return STEPS_MS[idx]
    }

    fun remainingMs(state: LockoutState, nowMs: Long): Long {
        if (state.lockedUntilMs <= 0L) return 0L
        val remaining = state.lockedUntilMs - nowMs
        // A lock further in the future than the maximum delay means the clock moved back: cap it.
        return if (remaining > STEPS_MS.last()) STEPS_MS.last() else remaining.coerceAtLeast(0L)
    }

    fun remainingMs(state: LockoutState, clock: LockoutClock): Long {
        if (state.lockedUntilMs <= 0L) return 0L
        if (state.bootCount != 0 && state.bootCount == clock.bootCount && state.lockedUntilElapsedMs > 0L) {
            return (state.lockedUntilElapsedMs - clock.elapsedMs).coerceIn(0L, STEPS_MS.last())
        }
        return remainingMs(state, clock.wallMs)
    }

    fun isLocked(state: LockoutState, nowMs: Long): Boolean = remainingMs(state, nowMs) > 0L
    fun isLocked(state: LockoutState, clock: LockoutClock): Boolean = remainingMs(state, clock) > 0L

    fun onFailure(state: LockoutState, nowMs: Long): LockoutState {
        val failures = state.failedAttempts + 1
        val delay = delayMsForFailures(failures)
        return LockoutState(failures, if (delay > 0) nowMs + delay else 0L)
    }

    fun onFailure(state: LockoutState, clock: LockoutClock): LockoutState {
        val failures = state.failedAttempts + 1
        val delay = delayMsForFailures(failures)
        return if (delay > 0) LockoutState(failures, clock.wallMs + delay, clock.elapsedMs + delay, clock.bootCount)
        else LockoutState(failures)
    }

    fun onSuccess(): LockoutState = LockoutState()
}
