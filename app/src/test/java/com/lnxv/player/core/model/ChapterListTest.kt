package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterListTest {
    private val list = listOf(ChapterItem(0, "Intro"), ChapterItem(60_000, "Act 1"), ChapterItem(180_000, "Act 2"))

    @Test fun sanitizeSortsDedupesAndNames() {
        val out = ChapterList.sanitize(listOf(ChapterItem(5000, " "), ChapterItem(-1, "bad"), ChapterItem(1000, "A"), ChapterItem(1000, "dup")))
        assertEquals(listOf(1000L, 5000L), out.map { it.startMs })
        assertEquals(listOf("A", "Chapter 2"), out.map { it.title })
    }

    @Test fun currentIndex() {
        assertEquals(-1, ChapterList.currentIndex(emptyList(), 10))
        assertEquals(-1, ChapterList.currentIndex(listOf(ChapterItem(5000, "x")), 1000))
        assertEquals(0, ChapterList.currentIndex(list, 0))
        assertEquals(1, ChapterList.currentIndex(list, 60_000))
        assertEquals(2, ChapterList.currentIndex(list, 999_999))
    }

    @Test fun nextChapter() {
        assertEquals(60_000L, ChapterList.next(list, 0)?.startMs)
        assertEquals(180_000L, ChapterList.next(list, 100_000)?.startMs)
        assertNull(ChapterList.next(list, 180_000))
    }

    @Test fun previousChapter() {
        assertEquals(60_000L, ChapterList.previous(list, 70_000)?.startMs) // restarts the current one
        assertEquals(0L, ChapterList.previous(list, 61_000)?.startMs) // just started: go back one
        assertEquals(0L, ChapterList.previous(list, 1_000)?.startMs)
        assertNull(ChapterList.previous(emptyList(), 0))
    }
}
