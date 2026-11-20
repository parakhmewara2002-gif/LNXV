package com.lnxv.player.core.model

import com.lnxv.player.core.model.Av1SoftwarePolicy.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class Av1SoftwarePolicyTest {
    private val n = Av1SoftwarePolicy.NORMAL
    private val low = Av1SoftwarePolicy.LOW_END

    @Test fun `1080p30 is allowed`() =
        assertEquals(Verdict.ALLOWED, Av1SoftwarePolicy.decide(DecoderMode.AUTO, 1920, 1080, 30f, n))

    @Test fun `NTSC 29_97 and 30_03 are allowed`() {
        assertEquals(Verdict.ALLOWED, Av1SoftwarePolicy.decide(DecoderMode.AUTO, 1920, 1080, 29.97f, n))
        assertEquals(Verdict.ALLOWED, Av1SoftwarePolicy.decide(DecoderMode.AUTO, 1920, 1080, 30.03f, n))
    }

    @Test fun `1080p60 exceeds the frame rate cap`() =
        assertEquals(Verdict.EXCEEDS_LIMIT, Av1SoftwarePolicy.decide(DecoderMode.AUTO, 1920, 1080, 60f, n))

    @Test fun `4K exceeds the resolution cap`() =
        assertEquals(Verdict.EXCEEDS_LIMIT, Av1SoftwarePolicy.decide(DecoderMode.AUTO, 3840, 2160, 24f, n))

    @Test fun `portrait 1080p is judged by side lengths not orientation`() {
        assertEquals(Verdict.ALLOWED, Av1SoftwarePolicy.decide(DecoderMode.AUTO, 1080, 1920, 30f, n))
        assertEquals(Verdict.EXCEEDS_LIMIT, Av1SoftwarePolicy.decide(DecoderMode.AUTO, 2160, 3840, 30f, n))
    }

    @Test fun `low end devices get the 720p cap`() {
        assertEquals(Verdict.ALLOWED, Av1SoftwarePolicy.decide(DecoderMode.AUTO, 1280, 720, 30f, low))
        assertEquals(Verdict.EXCEEDS_LIMIT, Av1SoftwarePolicy.decide(DecoderMode.AUTO, 1920, 1080, 30f, low))
    }

    @Test fun `limitsFor picks by device class`() {
        assertEquals(low, Av1SoftwarePolicy.limitsFor(true))
        assertEquals(n, Av1SoftwarePolicy.limitsFor(false))
    }

    @Test fun `hardware only mode never allows software`() =
        assertEquals(Verdict.BLOCKED_BY_MODE, Av1SoftwarePolicy.decide(DecoderMode.HARDWARE, 640, 360, 24f, n))

    @Test fun `software preferred is still capped`() {
        assertEquals(Verdict.ALLOWED, Av1SoftwarePolicy.decide(DecoderMode.SOFTWARE, 1280, 720, 24f, n))
        assertEquals(Verdict.EXCEEDS_LIMIT, Av1SoftwarePolicy.decide(DecoderMode.SOFTWARE, 3840, 2160, 24f, n))
    }

    @Test fun `unknown size and rate are not blocked`() =
        assertEquals(Verdict.ALLOWED, Av1SoftwarePolicy.decide(DecoderMode.AUTO, -1, -1, -1f, n))

    @Test fun `describe helpers`() {
        assertEquals("3840x2160 @ 60 fps", Av1SoftwarePolicy.describeVideo(3840, 2160, 60f))
        assertEquals("unknown size", Av1SoftwarePolicy.describeVideo(-1, -1, -1f))
        assertEquals("up to 1920x1088 at 30 fps", n.describe())
    }
}
