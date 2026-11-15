package com.lnxv.player.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTierTest {
    @Test fun lowRamFlagWins() = assertTrue(DeviceTier.isLowEnd(true, 8_000_000_000L, 120f))
    @Test fun twoToThreeGbIsLowEnd() {
        assertTrue(DeviceTier.isLowEnd(false, 2_000_000_000L, 90f))
        assertTrue(DeviceTier.isLowEnd(false, 2_900_000_000L, 120f))
    }
    @Test fun sixtyHertzFourGbIsLowEnd() = assertTrue(DeviceTier.isLowEnd(false, 3_800_000_000L, 60f))
    @Test fun capableDeviceIsNotLowEnd() {
        assertFalse(DeviceTier.isLowEnd(false, 8_000_000_000L, 120f))
        assertFalse(DeviceTier.isLowEnd(false, 3_800_000_000L, 120f))
        assertFalse(DeviceTier.isLowEnd(false, 8_000_000_000L, 60f))
    }
    @Test fun userOverrideBeatsDetection() {
        assertTrue(DeviceTier.resolveReduceEffects(EffectsMode.ON, false))
        assertFalse(DeviceTier.resolveReduceEffects(EffectsMode.OFF, true))
        assertTrue(DeviceTier.resolveReduceEffects(EffectsMode.AUTO, true))
        assertFalse(DeviceTier.resolveReduceEffects(EffectsMode.AUTO, false))
    }
    @Test fun parseFallsBackToAuto() {
        assertEquals(EffectsMode.AUTO, EffectsMode.parse("garbage"))
        assertEquals(EffectsMode.ON, EffectsMode.parse("ON"))
        assertEquals(EffectsMode.OFF, EffectsMode.ON.next())
    }
}
