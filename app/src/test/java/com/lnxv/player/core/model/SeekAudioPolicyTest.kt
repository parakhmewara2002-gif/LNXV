package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekPolicyTest {
    @Test fun dragging_uses_keyframe_seeks() {
        assertEquals(SeekMode.CLOSEST_SYNC, SeekPolicy.modeFor(scrubbing = true, exactSeekEnabled = true))
    }

    @Test fun release_is_exact_when_enabled() {
        assertEquals(SeekMode.EXACT, SeekPolicy.modeFor(scrubbing = false, exactSeekEnabled = true))
    }

    @Test fun exact_seek_off_always_snaps_to_keyframes() {
        assertEquals(SeekMode.CLOSEST_SYNC, SeekPolicy.modeFor(scrubbing = false, exactSeekEnabled = false))
        assertEquals(SeekMode.CLOSEST_SYNC, SeekPolicy.modeFor(scrubbing = true, exactSeekEnabled = false))
    }
}

class AudioBoostTest {
    @Test fun hundred_percent_is_no_gain() {
        assertEquals(0, AudioBoost.percentToMb(100))
    }

    @Test fun two_hundred_percent_is_about_six_db() {
        assertEquals(602, AudioBoost.percentToMb(200))
    }

    @Test fun percent_is_clamped_to_the_supported_range() {
        assertEquals(0, AudioBoost.percentToMb(50))
        assertEquals(AudioBoost.percentToMb(200), AudioBoost.percentToMb(400))
    }

    @Test fun mb_and_percent_round_trip_on_every_step() {
        AudioBoost.STEPS.forEach { assertEquals(it, AudioBoost.mbToPercent(AudioBoost.percentToMb(it))) }
    }

    @Test fun cycling_walks_the_steps_and_wraps() {
        assertEquals(125, AudioBoost.next(100))
        assertEquals(200, AudioBoost.next(175))
        assertEquals(100, AudioBoost.next(200))
    }

    @Test fun warning_only_above_150_percent() {
        assertFalse(AudioBoost.needsWarning(150))
        assertTrue(AudioBoost.needsWarning(175))
    }
}
