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
}
