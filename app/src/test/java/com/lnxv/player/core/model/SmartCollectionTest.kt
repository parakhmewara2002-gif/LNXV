package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses the primitive overloads: android.net.Uri (inside VideoItem) is not available in JVM unit tests. */
class SmartCollectionTest {
    private val now = 1_000_000_000L
    private val day = 24L * 3600

    @Test fun recentWindow() {
        assertTrue(SmartCollection.RECENT.matches(now - 3600, 1, 100_000, 0, now))
        assertFalse(SmartCollection.RECENT.matches(now - 8 * day, 1, 100_000, 0, now))
    }
    @Test fun unfinished() {
        assertTrue(SmartCollection.UNFINISHED.matches(now, 1, 100_000, 30_000, now))
        assertFalse(SmartCollection.UNFINISHED.matches(now, 1, 100_000, 2_000, now))
        assertFalse(SmartCollection.UNFINISHED.matches(now, 1, 100_000, 98_000, now))
    }
    @Test fun large() {
        assertTrue(SmartCollection.LARGE.matches(now, 2L shl 30, 1, 0, now))
        assertFalse(SmartCollection.LARGE.matches(now, 10L, 1, 0, now))
    }
    @Test fun watchedFractionClamped() {
        assertEquals(0.5f, SmartCollection.watchedFraction(100_000, 50_000), 0.0001f)
        assertEquals(1f, SmartCollection.watchedFraction(100_000, 500_000), 0f)
        assertEquals(0f, SmartCollection.watchedFraction(0, 5), 0f)
    }
    @Test fun newBadge() {
        assertTrue(SmartCollection.isNew(now - 60, 0L, now))
        assertFalse(SmartCollection.isNew(now - 60, 5L, now))
        assertFalse(SmartCollection.isNew(now - 4 * day, 0L, now))
    }
}
