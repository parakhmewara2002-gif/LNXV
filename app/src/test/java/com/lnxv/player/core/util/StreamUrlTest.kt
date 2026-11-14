package com.lnxv.player.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUrlTest {
    @Test fun acceptsSupportedSchemes() {
        assertTrue(StreamUrl.validate("https://a.com/x.m3u8", false) is StreamUrl.Result.Ok)
        assertTrue(StreamUrl.validate("rtsp://cam.local:554/live", false) is StreamUrl.Result.Ok)
        assertTrue(StreamUrl.validate("https://a.com/manifest.mpd", false) is StreamUrl.Result.Ok)
    }

    @Test fun cleartextHttpOnlyWhenAllowed() {
        assertTrue(StreamUrl.validate("http://a.com/x.mp4", false) is StreamUrl.Result.Error)
        assertTrue(StreamUrl.validate("http://a.com/x.mp4", true) is StreamUrl.Result.Ok)
    }

    @Test fun rejectsBadInput() {
        for (s in listOf("", "   ", "ftp://a.com/x", "javascript:alert(1)", "not a url", "https://", "file:///sdcard/x.mp4")) {
            assertTrue(s, StreamUrl.validate(s, true) is StreamUrl.Result.Error)
        }
    }

    @Test fun credentialsStripped() {
        assertEquals("https://a.com/x.m3u8?t=1", StreamUrl.stripCredentials("https://user:pw@a.com/x.m3u8?t=1"))
        assertEquals("https://a.com/x", StreamUrl.stripCredentials("https://a.com/x"))
        assertFalse(StreamUrl.stripCredentials("rtsp://admin:secret@10.0.0.2:554/s").contains("secret"))
    }

    @Test fun recentListDedupAndCap() {
        var l = emptyList<String>()
        for (i in 1..12) l = StreamUrl.addRecent(l, "https://a.com/$i")
        assertEquals(10, l.size)
        assertEquals("https://a.com/12", l.first())
        l = StreamUrl.addRecent(l, "https://a.com/5")
        assertEquals("https://a.com/5", l.first())
        assertEquals(l.toSet().size, l.size)
        assertEquals(l, StreamUrl.decodeList(StreamUrl.encodeList(l)))
    }

    @Test fun titles() {
        assertEquals("x.m3u8", StreamUrl.titleFor("https://a.com/p/x.m3u8"))
        assertEquals("a.com", StreamUrl.titleFor("https://a.com/"))
    }
}
