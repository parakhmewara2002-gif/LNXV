package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueLogicTest {
    private val ids = listOf(1L, 2L, 3L, 4L)

    @Test fun startsAtGivenVideoAndAdvances() {
        val q = QueueLogic(ids, startId = 2L)
        assertEquals(2L, q.currentId)
        assertEquals(3L, q.next())
        assertEquals(4L, q.next())
        assertNull(q.next())
        assertFalse(q.hasNext)
    }

    @Test fun previousStopsAtStartWithoutRepeat() {
        val q = QueueLogic(ids, startId = 2L)
        assertEquals(1L, q.previous())
        assertNull(q.previous())
    }

    @Test fun repeatAllWraps() {
        val q = QueueLogic(ids, startId = 4L, repeat = RepeatMode.ALL)
        assertTrue(q.hasNext)
        assertEquals(1L, q.next())
        assertEquals(4L, q.previous())
    }

    @Test fun singleItemNeverWraps() {
        val q = QueueLogic(listOf(9L), 9L, repeat = RepeatMode.ALL)
        assertFalse(q.hasNext); assertFalse(q.hasPrevious)
        assertNull(q.next())
    }

    @Test fun shuffleKeepsCurrentFirstAndVisitsEveryItemOnce() {
        val q = QueueLogic(ids, startId = 3L, shuffle = true, seed = 5L)
        assertEquals(3L, q.currentId)
        val seen = mutableListOf(3L)
        while (true) seen += q.next() ?: break
        assertEquals(ids.toSet(), seen.toSet())
        assertEquals(ids.size, seen.size)
    }

    @Test fun turningShuffleOffRestoresOrderAtCurrent() {
        val q = QueueLogic(ids, startId = 1L, shuffle = true, seed = 1L)
        q.next()
        val cur = q.currentId
        q.setShuffle(false)
        assertEquals(cur, q.currentId)
        assertEquals(ids.indexOf(cur) + 1 < ids.size, q.hasNext)
    }

    @Test fun repeatCycle() {
        val q = QueueLogic(ids, 1L)
        assertEquals(RepeatMode.ALL, q.cycleRepeat())
        assertEquals(RepeatMode.ONE, q.cycleRepeat())
        assertEquals(RepeatMode.OFF, q.cycleRepeat())
    }

    @Test fun unknownStartFallsBackToFirst() {
        assertEquals(1L, QueueLogic(ids, 99L).currentId)
    }

    @Test fun emptyQueueIsSafe() {
        val q = QueueLogic(emptyList(), 1L)
        assertNull(q.next()); assertNull(q.previous()); assertNull(q.currentId)
    }
}
