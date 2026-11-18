package com.lnxv.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleFormatTest {
    @Test fun `srt`() = assertEquals(SubtitleFormat.SRT, SubtitleFormat.detect("1\n00:00:01,000 --> 00:00:02,000\nHi\n"))
    @Test fun `vtt with bom`() = assertEquals(SubtitleFormat.VTT, SubtitleFormat.detect("﻿WEBVTT\n\n00:01.000 --> 00:02.000\nHi"))
    @Test fun `ass`() = assertEquals(SubtitleFormat.ASS, SubtitleFormat.detect("[Script Info]\nTitle: x\n[V4+ Styles]\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hi"))
    @Test fun `ssa old style`() = assertEquals(SubtitleFormat.ASS, SubtitleFormat.detect("[Script Info]\n[V4 Styles]\n"))
    @Test fun `ttml`() = assertEquals(SubtitleFormat.TTML, SubtitleFormat.detect("<?xml version=\"1.0\"?>\n<tt xmlns=\"http://www.w3.org/ns/ttml\"><body/></tt>"))
    @Test fun `plain text is not a subtitle`() = assertNull(SubtitleFormat.detect("hello world, no timing here"))
    @Test fun `extension mapping`() {
        assertEquals(SubtitleFormat.ASS, SubtitleFormat.fromFileName("a.SSA"))
        assertEquals(SubtitleFormat.SRT, SubtitleFormat.fromFileName("movie.en.srt"))
        assertNull(SubtitleFormat.fromFileName("movie.idx"))
        assertNull(SubtitleFormat.fromFileName(null))
    }
    @Test fun `only text formats with timestamps support delay shifting`() {
        assertEquals(listOf(true, true, true, false), SubtitleFormat.values().map { it.supportsDelay })
    }
}

class AssShifterTest {
    @Test fun `shifts start and end of dialogue lines only`() {
        val src = "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,Hello, world"
        val out = AssShifter.shift(src, 1000)
        assertEquals(true, out.contains("Dialogue: 0,0:00:02.50,0:00:04.00,Default,,0,0,0,,Hello, world"))
        assertEquals(true, out.contains("Format: Layer, Start, End"))
    }
    @Test fun `negative shift clamps at zero`() = assertEquals("0:00:00.00", AssShifter.shiftTime("0:00:01.00", -5000))
    @Test fun `hours roll over`() = assertEquals("1:00:00.00", AssShifter.shiftTime("0:59:59.00", 1000))
    @Test fun `zero offset is a no-op`() = assertEquals("x", AssShifter.shift("x", 0))
}
