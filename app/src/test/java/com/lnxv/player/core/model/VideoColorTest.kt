package com.lnxv.player.core.model

import org.junit.Assert.*
import org.junit.Test

class VideoColorTest {
    @Test fun neutralByDefault() = assertTrue(VideoColor().isNeutral)
    @Test fun cycleStepsUp() = assertEquals(0.1f, VideoColor.cycle(0f), 1e-4f)
    @Test fun cycleWrapsAtLimit() = assertEquals(-0.5f, VideoColor.cycle(0.5f), 1e-4f)
    @Test fun cycleVisitsZeroAgain() {
        var v = -0.5f; var seen0 = false
        repeat(11) { v = VideoColor.cycle(v); if (Math.abs(v) < 1e-4f) seen0 = true }
        assertTrue(seen0)
    }
    @Test fun nonNeutralDetected() = assertFalse(VideoColor(contrast = 0.2f).isNeutral)
}
