package com.lnxv.player.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekMathTest {

    private val unset = Long.MIN_VALUE + 1 // androidx.media3.common.C.TIME_UNSET
    private val twoMinutes = 120_000L

    @Test
    fun clamp_unknownDuration_doesNotJumpToStart() {
        // Regression: this used to clamp to 0 and move playback BACKWARD.
        assertEquals(5_000L, SeekMath.clamp(5_000L, unset))
        assertEquals(5_000L, SeekMath.clamp(5_000L, 0L))
    }

    @Test
    fun clamp_knownDuration_staysInRange() {
        assertEquals(0L, SeekMath.clamp(-10L, twoMinutes))
        assertEquals(twoMinutes, SeekMath.clamp(999_999L, twoMinutes))
        assertEquals(60_000L, SeekMath.clamp(60_000L, twoMinutes))
    }

    @Test
    fun relativeForward_movesForward() {
        assertEquals(40_000L, SeekMath.relativeTarget(30_000L, 10_000L, twoMinutes))
        assertEquals(40_000L, SeekMath.relativeTarget(30_000L, 10_000L, unset))
    }

    @Test
    fun relativeBackward_movesBackward_andStopsAtZero() {
        assertEquals(20_000L, SeekMath.relativeTarget(30_000L, -10_000L, twoMinutes))
        assertEquals(0L, SeekMath.relativeTarget(3_000L, -10_000L, twoMinutes))
    }

    @Test
    fun seekAtBeginningAndEnd() {
        assertEquals(0L, SeekMath.relativeTarget(0L, -10_000L, twoMinutes))
        assertEquals(twoMinutes, SeekMath.relativeTarget(twoMinutes, 10_000L, twoMinutes))
        assertEquals(twoMinutes, SeekMath.relativeTarget(115_000L, 10_000L, twoMinutes))
    }

    @Test
    fun rapidRepeatedForwardSeeks_areMonotonic() {
        var position = 0L
        var previous = -1L
        repeat(20) {
            position = SeekMath.relativeTarget(position, 10_000L, twoMinutes)
            assertTrue("position must never go backward", position >= previous)
            previous = position
        }
        assertEquals(twoMinutes, position)
    }

    @Test
    fun rapidAlternatingSeeks_endWhereExpected() {
        var position = 60_000L
        repeat(10) {
            position = SeekMath.relativeTarget(position, 10_000L, twoMinutes)
            position = SeekMath.relativeTarget(position, -10_000L, twoMinutes)
        }
        assertEquals(60_000L, position)
    }

    @Test
    fun fractionConversion_roundTrips() {
        assertEquals(60_000L, SeekMath.fractionToPosition(0.5f, twoMinutes))
        assertEquals(0.5f, SeekMath.positionToFraction(60_000L, twoMinutes), 0.0001f)
        assertEquals(0L, SeekMath.fractionToPosition(0.5f, unset))
        assertEquals(0f, SeekMath.positionToFraction(60_000L, unset), 0f)
        assertEquals(twoMinutes, SeekMath.fractionToPosition(2f, twoMinutes))
    }

    @Test
    fun swipeSeek_rightIsForward_leftIsBackward() {
        val forward = SeekMath.dragTarget(30_000L, 540f, 1080f, twoMinutes)
        val backward = SeekMath.dragTarget(30_000L, -270f, 1080f, twoMinutes)
        assertEquals(75_000L, forward)
        assertEquals(7_500L, backward)
    }
}
