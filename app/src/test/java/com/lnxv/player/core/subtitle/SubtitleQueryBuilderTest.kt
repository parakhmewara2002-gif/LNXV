package com.lnxv.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleQueryBuilderTest {
    @Test fun `movie release name`() {
        val p = SubtitleQueryBuilder.parse("The.Matrix.1999.1080p.BluRay.x264.mkv")
        assertEquals("The Matrix 1999", p.query); assertNull(p.season); assertNull(p.episode)
    }
    @Test fun `tv episode`() {
        val p = SubtitleQueryBuilder.parse("Breaking.Bad.S02E05.720p.HDTV.x264.mp4")
        assertEquals("Breaking Bad", p.query); assertEquals(2, p.season); assertEquals(5, p.episode)
    }
    @Test fun `brackets and underscores`() {
        assertEquals("My Holiday", SubtitleQueryBuilder.parse("[Group] My_Holiday (2020) [1080p].mp4").query)
    }
    @Test fun `plain name keeps all words`() = assertEquals("Family Trip Goa", SubtitleQueryBuilder.parse("Family Trip Goa.mp4").query)
    @Test fun `name that is only tags falls back to the words`() = assertEquals("1080p", SubtitleQueryBuilder.parse("1080p.mkv").query)
    @Test fun `name without extension`() = assertEquals("Holiday", SubtitleQueryBuilder.parse("Holiday").query)
}
