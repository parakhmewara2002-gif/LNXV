package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {
    private fun m(q: String, title: String = "Holiday Trip", folder: String = "Camera", path: String = "/a/holiday.mkv", h: Int = 1080, size: Long = 600L shl 20) =
        SearchQuery.parse(q).matches(title, folder, path, h, size)

    @Test fun plainTermsMatchTitleOrFolder() {
        assertTrue(m("holiday"))
        assertTrue(m("camera"))
        assertFalse(m("nope"))
        assertTrue(m("holiday trip"))
    }
    @Test fun extFilter() {
        assertTrue(m("ext:mkv"))
        assertFalse(m("ext:mp4"))
        assertTrue(m("type:.mkv"))
    }
    @Test fun resolutionFilter() {
        assertTrue(m("res:1080"))
        assertFalse(m("res:4k"))
        assertTrue(m("res:hd"))
    }
    @Test fun sizeFilter() {
        assertTrue(m("size>500mb"))
        assertFalse(m("size>1gb"))
        assertTrue(m("size<1gb"))
        assertFalse(m("size<100mb"))
    }
    @Test fun combined() = assertTrue(m("holiday ext:mkv res:720 size>1mb"))
    @Test fun unknownTokensAreWords() {
        val q = SearchQuery.parse("res:abc ext:")
        assertEquals(listOf("res:abc", "ext:"), q.terms)
        assertNull(q.ext)
    }
    @Test fun emptyQuery() = assertTrue(SearchQuery.parse("   ").isEmpty)
}

class SearchQueryFilterTest {
    @org.junit.Test fun durationAndFolder() {
        val q = SearchQuery.parse("dur>30m folder:Movies")
        org.junit.Assert.assertEquals(1_800_000L, q.minDurationMs)
        org.junit.Assert.assertTrue(q.matches("a", "My Movies", "/x/a.mkv", 1080, 1L, 2_000_000L))
        org.junit.Assert.assertFalse(q.matches("a", "My Movies", "/x/a.mkv", 1080, 1L, 60_000L))
        org.junit.Assert.assertFalse(q.matches("a", "Clips", "/x/a.mkv", 1080, 1L, 2_000_000L))
    }
    @org.junit.Test fun maxDurationSeconds() {
        val q = SearchQuery.parse("dur<90s")
        org.junit.Assert.assertEquals(90_000L, q.maxDurationMs)
    }
}
