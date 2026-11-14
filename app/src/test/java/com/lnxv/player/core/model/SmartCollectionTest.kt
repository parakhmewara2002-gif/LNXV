package com.lnxv.player.core.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCollectionTest {
    private val now = 1_000_000_000L

    private fun v(added: Long = now, size: Long = 1L, dur: Long = 100_000L, pos: Long = 0L, played: Long = 0L) =
        VideoItem(
            id = 1, title = "t", uri = Uri.EMPTY, path = "", durationMs = dur, sizeBytes = size,
            width = 0, height = 0, mimeType = "video/mp4", dateAddedSec = added,
            folderPath = "", folderName = "", lastPositionMs = pos, lastPlayedTimestamp = played
        )

    @Test fun recentWindow() {
        assertTrue(SmartCollection.RECENT.matches(v(added = now - 3600), now))
        assertFalse(SmartCollection.RECENT.matches(v(added = now - 8L * 24 * 3600), now))
    }
    @Test fun unfinished() {
        assertTrue(SmartCollection.UNFINISHED.matches(v(pos = 30_000), now))
        assertFalse(SmartCollection.UNFINISHED.matches(v(pos = 2_000), now))
        assertFalse(SmartCollection.UNFINISHED.matches(v(pos = 98_000), now))
    }
    @Test fun large() {
        assertTrue(SmartCollection.LARGE.matches(v(size = 2L shl 30), now))
        assertFalse(SmartCollection.LARGE.matches(v(size = 10L), now))
    }
    @Test fun watchedFractionClamped() {
        assertEquals(0.5f, SmartCollection.watchedFraction(v(pos = 50_000)), 0.0001f)
        assertEquals(1f, SmartCollection.watchedFraction(v(pos = 500_000)), 0f)
        assertEquals(0f, SmartCollection.watchedFraction(v(dur = 0L, pos = 5L)), 0f)
    }
    @Test fun newBadge() {
        assertTrue(SmartCollection.isNew(v(added = now - 60), now))
        assertFalse(SmartCollection.isNew(v(added = now - 60, played = 5L), now))
    }
}
