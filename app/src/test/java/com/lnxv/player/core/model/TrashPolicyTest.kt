package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrashPolicyTest {
    @Test fun unknownExpiry() = assertNull(TrashPolicy.daysLeft(0, 1000))

    @Test fun expiredIsZero() {
        assertEquals(0, TrashPolicy.daysLeft(1000, 1000))
        assertEquals(0, TrashPolicy.daysLeft(500, 1000))
    }

    @Test fun roundsUp() {
        assertEquals(1, TrashPolicy.daysLeft(1001, 1000))
        assertEquals(1, TrashPolicy.daysLeft(1000 + 86_400, 1000))
        assertEquals(2, TrashPolicy.daysLeft(1000 + 86_401, 1000))
        assertEquals(30, TrashPolicy.daysLeft(1000 + 30 * 86_400L, 1000))
    }

    @Test fun sortsNewestExpiryFirst() {
        val a = TrashItem(1, "a", 1, 100)
        val b = TrashItem(2, "b", 1, 300)
        val c = TrashItem(3, "c", 1, 200)
        assertEquals(listOf(2L, 3L, 1L), TrashPolicy.sorted(listOf(a, b, c)).map { it.id })
    }
}
