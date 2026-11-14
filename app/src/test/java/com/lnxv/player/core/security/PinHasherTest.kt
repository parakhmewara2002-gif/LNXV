package com.lnxv.player.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    private val iters = 1000 // fast for tests; production uses DEFAULT_ITERATIONS

    @Test fun roundTrip() {
        val salt = PinHasher.newSalt()
        val h = PinHasher.hash("123456", salt, iters)
        assertTrue(PinHasher.verify("123456", salt, h, iters))
        assertFalse(PinHasher.verify("123457", salt, h, iters))
    }

    @Test fun saltsAreRandomAndChangeHash() {
        val a = PinHasher.newSalt(); val b = PinHasher.newSalt()
        assertEquals(PinHasher.SALT_BYTES, a.size)
        assertFalse(a.contentEquals(b))
        assertFalse(PinHasher.hash("1234", a, iters).contentEquals(PinHasher.hash("1234", b, iters)))
    }

    @Test fun iterationCountMatters() {
        val s = PinHasher.newSalt()
        assertFalse(PinHasher.hash("1234", s, 1000).contentEquals(PinHasher.hash("1234", s, 1001)))
    }

    @Test fun defaultIterationsMeetMinimum() {
        assertTrue(PinHasher.DEFAULT_ITERATIONS >= 210_000)
    }

    @Test fun pinValidation() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin(""))
    }

    @Test fun emptyInputsNeverVerify() {
        assertFalse(PinHasher.verify("1234", ByteArray(0), ByteArray(32), iters))
        assertFalse(PinHasher.verify("1234", PinHasher.newSalt(), ByteArray(0), iters))
    }

    @Test fun legacyHashStillVerifiesForMigration() {
        val stored = PinHasher.legacyHash("4321")
        assertEquals(64, stored.length)
        assertTrue(PinHasher.verifyLegacy("4321", stored))
        assertFalse(PinHasher.verifyLegacy("4322", stored))
        assertFalse(PinHasher.verifyLegacy("4321", ""))
        assertNotEquals(stored, PinHasher.legacyHash("4322"))
    }
}
