package com.lnxv.player.playback.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackNamesTest {
    @Test fun unknownLanguageIsNull() {
        assertNull(TrackNames.languageName(null))
        assertNull(TrackNames.languageName("und"))
        assertNull(TrackNames.languageName(" "))
    }
    @Test fun channelLayouts() {
        assertEquals("Stereo", TrackNames.channelLayout(2))
        assertEquals("5.1", TrackNames.channelLayout(6))
        assertNull(TrackNames.channelLayout(0))
    }
    @Test fun codecs() {
        assertEquals("AAC", TrackNames.audioCodec("audio/mp4a-latm"))
        assertEquals("E-AC3", TrackNames.audioCodec("audio/eac3"))
        assertEquals("SRT", TrackNames.subtitleFormat("application/x-subrip"))
        assertNull(TrackNames.subtitleFormat("x/unknown"))
    }
    @Test fun releaseTagTitleIsOnlyUsedWithoutLanguage() {
        assertEquals("(Movies4u.Foo). · SRT", TrackNames.subtitleLabel("und", "(Movies4u.Foo).", "application/x-subrip", 0, false, false))
        assertEquals("Subtitle 2 · PGS · Forced", TrackNames.subtitleLabel(null, null, "application/pgs", 1, true, false))
        assertEquals("Track 1 · Stereo · AAC", TrackNames.audioLabel("und", null, 2, "audio/mp4a-latm", 0))
    }
}
