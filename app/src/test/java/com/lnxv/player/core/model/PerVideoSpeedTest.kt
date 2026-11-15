package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerVideoSpeedTest {
    @Test fun roundTrip() {
        val e = PerVideoSpeed.with(PerVideoSpeed.with("", 1L, 1.5f), 2L, 2.0f)
        assertEquals(1.5f, PerVideoSpeed.get(e, 1L)!!, 0f)
        assertEquals(2.0f, PerVideoSpeed.get(e, 2L)!!, 0f)
    }
    @Test fun defaultSpeedRemovesEntry() {
        val e = PerVideoSpeed.with(PerVideoSpeed.with("", 1L, 1.5f), 1L, 1.0f)
        assertNull(PerVideoSpeed.get(e, 1L))
    }
    @Test fun transientVideoIgnored() = assertEquals("", PerVideoSpeed.with("", -1L, 2f))
    @Test fun garbageIgnored() {
        assertEquals(1, PerVideoSpeed.decode("x;1=2.0;3=99;=;4=abc").size)
    }
    @Test fun capped() {
        var e = ""
        for (i in 1L..300L) e = PerVideoSpeed.with(e, i, 1.5f)
        assertEquals(PerVideoSpeed.MAX_ENTRIES, PerVideoSpeed.decode(e).size)
        assertEquals(1.5f, PerVideoSpeed.get(e, 300L)!!, 0f)
    }
}
