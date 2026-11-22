package com.lnxv.player.multiscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiScreenMathTest {
    private val phone = DpSize2(800f, 360f)

    @Test fun fitCentresWideVideoInTallCanvas() {
        val r = SpanGeometry.fit(800f, 760f, 16f / 9f)
        assertEquals(800f, r.width, 0.01f)
        assertEquals(450f, r.height, 0.01f)
        assertEquals(155f, r.top, 0.01f)
    }

    @Test fun stackedSpanSplitsVideoAtTheBezel() {
        // 2 phones 800x360 stacked with a 40 dp gap: canvas 800x760, 16:9 video fills the width => 450 high
        val top = SpanGeometry.span(phone, phone, stacked = true, first = true, bezelDp = 40f, aspect = 16f / 9f)
        val bottom = SpanGeometry.span(phone, phone, stacked = true, first = false, bezelDp = 40f, aspect = 16f / 9f)
        assertEquals(top.width, bottom.width, 0.01f)
        assertEquals(top.height, bottom.height, 0.01f)
        // the bottom phone's origin is 400 dp lower on the canvas, so its video top is 400 dp higher
        assertEquals(400f, top.top - bottom.top, 0.01f)
    }

    @Test fun sideBySideOffsetsHorizontally() {
        val left = SpanGeometry.span(phone, phone, stacked = false, first = true, bezelDp = 30f, aspect = 16f / 9f)
        val right = SpanGeometry.span(phone, phone, stacked = false, first = false, bezelDp = 30f, aspect = 16f / 9f)
        assertEquals(830f, left.left - right.left, 0.01f)
    }

    @Test fun biggerBezelNeverShrinksOffsetBetweenPhones() {
        val a = SpanGeometry.span(phone, phone, true, true, 10f, 2f).top - SpanGeometry.span(phone, phone, true, false, 10f, 2f).top
        val b = SpanGeometry.span(phone, phone, true, true, 60f, 2f).top - SpanGeometry.span(phone, phone, true, false, 60f, 2f).top
        assertEquals(50f, b - a, 0.01f)
    }

    @Test fun clockOffsetAndTarget() {
        assertEquals(1000L, SyncMath.clockOffset(100L, 120L, 1110L))
        assertEquals(10_500L, SyncMath.targetPosition(10_000L, 5_000L, 5_500L, true, 1f))
        assertEquals(10_000L, SyncMath.targetPosition(10_000L, 5_000L, 5_500L, false, 1f))
    }

    @Test fun decideThresholds() {
        assertTrue(SyncMath.decide(400L, 1f, true) is SyncMath.Action.Seek)
        val slow = SyncMath.decide(100L, 1f, true) as SyncMath.Action.Speed
        assertTrue(slow.factor < 1f)
        val fast = SyncMath.decide(-100L, 1f, true) as SyncMath.Action.Speed
        assertTrue(fast.factor > 1f)
        assertEquals(1f, (SyncMath.decide(10L, 1f, true) as SyncMath.Action.Speed).factor, 0.0001f)
        // seek not allowed (cool-down) => nudge instead
        assertTrue(SyncMath.decide(400L, 1f, false) is SyncMath.Action.Speed)
    }
}
