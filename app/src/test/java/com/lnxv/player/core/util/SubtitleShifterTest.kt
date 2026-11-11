package com.lnxv.player.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleShifterTest {

    private val srt = "1\n00:00:01,000 --> 00:00:03,500\nHello\n\n2\n00:01:00,250 --> 00:01:02,000\nWorld\n"

    @Test
    fun zeroOffset_returnsSameContent() {
        assertEquals(srt, SubtitleShifter.shift(srt, 0L))
    }

    @Test
    fun positiveOffset_delaysCues_andKeepsText() {
        val out = SubtitleShifter.shift(srt, 1500L)
        assertTrue(out.contains("00:00:02,500 --> 00:00:05,000"))
        assertTrue(out.contains("00:01:01,750 --> 00:01:03,500"))
        assertTrue(out.contains("Hello"))
        assertTrue(out.contains("World"))
    }

    @Test
    fun negativeOffset_neverGoesBelowZero() {
        val out = SubtitleShifter.shift(srt, -2000L)
        assertTrue(out.contains("00:00:00,000 --> 00:00:01,500"))
    }

    @Test
    fun hourBoundary_isHandled() {
        val out = SubtitleShifter.shift("1\n00:59:59,500 --> 01:00:00,500\nX\n", 1000L)
        assertTrue(out.contains("01:00:00,500 --> 01:00:01,500"))
    }

    @Test
    fun vtt_withoutHours_keepsDotAndNoHours() {
        val out = SubtitleShifter.shift("WEBVTT\n\n00:01.000 --> 00:03.000\nHi\n", 2000L)
        assertTrue(out.contains("00:03.000 --> 00:05.000"))
    }

    @Test
    fun textLinesContainingTimes_areNotTouched() {
        val src = "1\n00:00:01,000 --> 00:00:02,000\nMeet me at 10:30:15,500 ok\n"
        val out = SubtitleShifter.shift(src, 1000L)
        assertTrue(out.contains("Meet me at 10:30:15,500 ok"))
    }

    @Test
    fun looksLikeSubtitle_detectsFormat() {
        assertTrue(SubtitleShifter.looksLikeSubtitle(srt))
        assertFalse(SubtitleShifter.looksLikeSubtitle("[Script Info]\nTitle: x"))
    }
}
