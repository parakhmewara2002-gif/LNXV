package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateFinderTest {
    private data class F(val name: String, val size: Long, val dur: Long)

    private fun g(vararg f: F) = DuplicateFinder.groups(f.toList(), { it.size }, { it.dur })

    @Test fun groupsSameSizeAndDuration() {
        val r = g(F("a", 100, 10), F("b", 100, 10), F("c", 100, 11), F("d", 200, 10))
        assertEquals(1, r.size)
        assertEquals(listOf("a", "b"), r[0].map { it.name })
    }
    @Test fun ignoresUnknownMetadata() = assertEquals(0, g(F("a", 0, 10), F("b", 0, 10), F("c", 5, 0), F("d", 5, 0)).size)
    @Test fun noDuplicates() = assertEquals(0, g(F("a", 1, 1), F("b", 2, 2)).size)
    @Test fun largestGroupFirst() {
        val r = g(F("a", 10, 1), F("b", 10, 1), F("c", 99, 2), F("d", 99, 2))
        assertEquals(99L, r[0][0].size)
    }
}
