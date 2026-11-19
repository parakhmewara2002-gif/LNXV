package com.lnxv.player.core.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarkListTest {
    @Test fun addKeepsSortedOrder() {
        var l = BookmarkList.add(emptyList(), 30_000)
        l = BookmarkList.add(l, 10_000, "intro")
        assertEquals(listOf(10_000L, 30_000L), l.map { it.positionMs })
        assertEquals("intro", l[0].label)
    }

    @Test fun addWithinMergeWindowIsIgnored() {
        val l = BookmarkList.add(emptyList(), 10_000)
        assertEquals(l, BookmarkList.add(l, 10_500))
        assertEquals(2, BookmarkList.add(l, 11_000).size)
    }

    @Test fun negativePositionClampsToZero() {
        assertEquals(0L, BookmarkList.add(emptyList(), -5)[0].positionMs)
    }

    @Test fun capIsEnforced() {
        var l = emptyList<Bookmark>()
        for (i in 0 until BookmarkList.MAX + 20) l = BookmarkList.add(l, i * 5_000L)
        assertEquals(BookmarkList.MAX, l.size)
    }

    @Test fun removeAndRename() {
        var l = BookmarkList.add(BookmarkList.add(emptyList(), 1_000), 5_000)
        l = BookmarkList.rename(l, 5_000, "  scene\tone\n ")
        assertEquals("scene one", l[1].label)
        l = BookmarkList.remove(l, 1_000)
        assertEquals(listOf(5_000L), l.map { it.positionMs })
    }

    @Test fun nextAndPrevious() {
        val l = listOf(Bookmark(10_000), Bookmark(20_000), Bookmark(30_000))
        assertEquals(20_000L, BookmarkList.next(l, 10_000)?.positionMs)
        assertNull(BookmarkList.next(l, 30_000))
        assertEquals(10_000L, BookmarkList.previous(l, 20_000)?.positionMs)
        // Just after a bookmark, "previous" goes to the one before it, not the same one.
        assertEquals(10_000L, BookmarkList.previous(l, 20_800)?.positionMs)
        assertNull(BookmarkList.previous(l, 5_000))
    }

    @Test fun encodeDecodeRoundTrip() {
        val l = listOf(Bookmark(1_000, "a"), Bookmark(2_500), Bookmark(9_000, "भाग दो"))
        assertEquals(l, BookmarkList.decode(BookmarkList.encode(l)))
    }

    @Test fun decodeIsTolerantOfGarbage() {
        val l = BookmarkList.decode("abc\nx\t1\n-5\tneg\n7000\tok\n7000\tdup\n\n3000")
        assertEquals(listOf(3_000L, 7_000L), l.map { it.positionMs })
        assertEquals("ok", l[1].label)
        assertEquals(emptyList<Bookmark>(), BookmarkList.decode(null))
        assertEquals(emptyList<Bookmark>(), BookmarkList.decode("  "))
    }

    @Test fun labelIsCappedAndCleaned() {
        assertEquals(BookmarkList.MAX_LABEL, BookmarkList.cleanLabel("x".repeat(500)).length)
    }

    @Test fun formatTime() {
        assertEquals("0:00", BookmarkList.formatTime(-10))
        assertEquals("1:05", BookmarkList.formatTime(65_000))
        assertEquals("1:01:01", BookmarkList.formatTime(3_661_000))
    }
}
