package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqPresetTest {
    @Test fun flatIsZero() = assertTrue(EqPreset.FLAT.levels(5, -1500, 1500).all { it == 0 })
    @Test fun bandCountRespected() = assertEquals(10, EqPreset.BASS.levels(10, -1500, 1500).size)
    @Test fun withinRange() = EqPreset.values().forEach { p ->
        assertTrue(p.levels(7, -1000, 1200).all { it in -1000..1200 })
    }
    @Test fun bassBoostsLowBands() {
        val l = EqPreset.BASS.levels(5, -1500, 1500)
        assertTrue(l.first() > l.last())
    }
    @Test fun zeroBands() = assertEquals(0, EqPreset.BASS.levels(0, -1, 1).size)
    @Test fun cycles() = assertEquals(EqPreset.FLAT, EqPreset.LOUDNESS.next())
}
