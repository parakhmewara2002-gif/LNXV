package com.lnxv.player.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutPolicyTest {
    @Test fun noDelayForFirstFourFailures() {
        for (n in 0..4) assertEquals(0L, LockoutPolicy.delayMsForFailures(n))
    }

    @Test fun exponentialSchedule() {
        assertEquals(30_000L, LockoutPolicy.delayMsForFailures(5))
        assertEquals(60_000L, LockoutPolicy.delayMsForFailures(6))
        assertEquals(300_000L, LockoutPolicy.delayMsForFailures(7))
        assertEquals(900_000L, LockoutPolicy.delayMsForFailures(8))
        assertEquals(3_600_000L, LockoutPolicy.delayMsForFailures(9))
        assertEquals(3_600_000L, LockoutPolicy.delayMsForFailures(500))
    }

    @Test fun fifthFailureLocks() {
        var s = LockoutState()
        val now = 1_000_000L
        repeat(4) { s = LockoutPolicy.onFailure(s, now) }
        assertFalse(LockoutPolicy.isLocked(s, now))
        s = LockoutPolicy.onFailure(s, now)
        assertTrue(LockoutPolicy.isLocked(s, now))
        assertEquals(30_000L, LockoutPolicy.remainingMs(s, now))
        assertFalse(LockoutPolicy.isLocked(s, now + 30_000L))
    }

    @Test fun successResets() {
        assertEquals(LockoutState(), LockoutPolicy.onSuccess())
    }

    @Test fun clockMovedBackIsCapped() {
        val s = LockoutState(9, 10_000_000_000L)
        assertEquals(3_600_000L, LockoutPolicy.remainingMs(s, 0L))
    }

    @Test fun monotonicClockIgnoresWallClockChanges() {
        val c0 = LockoutClock(wallMs = 1_000_000L, elapsedMs = 50_000L, bootCount = 7)
        var s = LockoutState()
        repeat(5) { s = LockoutPolicy.onFailure(s, c0) }
        // wall clock pushed a day ahead, only 10 s really elapsed: still locked for the remaining 20 s
        val forward = LockoutClock(wallMs = 1_000_000L + 86_400_000L, elapsedMs = 60_000L, bootCount = 7)
        assertEquals(20_000L, LockoutPolicy.remainingMs(s, forward))
        // wall clock set back to 0, 30 s really elapsed: unlocked
        val back = LockoutClock(wallMs = 0L, elapsedMs = 80_000L, bootCount = 7)
        assertEquals(0L, LockoutPolicy.remainingMs(s, back))
        assertFalse(LockoutPolicy.isLocked(s, back))
    }

    @Test fun afterRebootFallsBackToWallClock() {
        val c0 = LockoutClock(wallMs = 1_000_000L, elapsedMs = 50_000L, bootCount = 7)
        var s = LockoutState()
        repeat(5) { s = LockoutPolicy.onFailure(s, c0) }
        val rebooted = LockoutClock(wallMs = 1_010_000L, elapsedMs = 1_000L, bootCount = 8)
        assertEquals(20_000L, LockoutPolicy.remainingMs(s, rebooted))
    }

    @Test fun legacyStateWithoutMonotonicDataUsesWallClock() {
        val s = LockoutState(failedAttempts = 5, lockedUntilMs = 1_030_000L)
        assertEquals(30_000L, LockoutPolicy.remainingMs(s, LockoutClock(wallMs = 1_000_000L, elapsedMs = 5L, bootCount = 3)))
    }
}
