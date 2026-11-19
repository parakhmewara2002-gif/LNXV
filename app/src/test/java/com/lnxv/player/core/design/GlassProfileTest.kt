package com.lnxv.player.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassProfileTest {
    private fun r(mode: EffectsMode, lowEnd: Boolean = false, api: Int = 34, ram: Long = 8_000_000_000L, hz: Float = 120f, sysOff: Boolean = false) =
        GlassProfileResolver.resolve(mode, lowEnd, api, ram, hz, sysOff)

    @Test fun flagshipAutoIsHigh() = assertEquals(GlassProfile.HIGH, r(EffectsMode.AUTO))
    @Test fun lowEndAutoIsLow() = assertEquals(GlassProfile.LOW, r(EffectsMode.AUTO, lowEnd = true, ram = 2_900_000_000L))
    @Test fun sixtyHertzMidRangeIsBalanced() = assertEquals(GlassProfile.BALANCED, r(EffectsMode.AUTO, ram = 6_000_000_000L, hz = 60f))
    @Test fun preAndroid13NeverHighInAuto() = assertEquals(GlassProfile.BALANCED, r(EffectsMode.AUTO, api = 32))
    @Test fun userOnMeansLowOffMeansFull() {
        assertEquals(GlassProfile.LOW, r(EffectsMode.ON))
        assertEquals(GlassProfile.HIGH, r(EffectsMode.OFF, lowEnd = true, ram = 3_000_000_000L, hz = 60f))
        assertEquals(GlassProfile.BALANCED, r(EffectsMode.OFF, api = 30))
    }
    @Test fun systemRemoveAnimationsForcesLow() = assertEquals(GlassProfile.LOW, r(EffectsMode.OFF, sysOff = true))
    @Test fun lensOnlyOnHighAndApi33() {
        assertTrue(lensSupported(GlassProfile.HIGH, 33))
        assertFalse(lensSupported(GlassProfile.HIGH, 32))
        assertFalse(lensSupported(GlassProfile.BALANCED, 34))
        assertFalse(lensSupported(GlassProfile.LOW, 34))
    }
    @Test fun layeredLookOnlyAboveLow() {
        assertFalse(GlassProfile.LOW.layeredHighlights)
        assertTrue(GlassProfile.BALANCED.layeredHighlights)
    }
}
