package com.lnxv.player.core.enhancer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureSettingsTest {
    @Test fun `default is neutral`() = assertTrue(PictureSettings().isNeutral)

    @Test fun `any change breaks neutrality`() {
        assertFalse(PictureSettings(brightness = 0.1f).isNeutral)
        assertFalse(PictureSettings(gamma = 1.1f).isNeutral)
        assertFalse(PictureSettings(sharpness = 0.1f).isNeutral)
        assertFalse(PictureSettings(vivid = 0.1f).isNeutral)
        assertFalse(PictureSettings(tone = Tone.WARM).isNeutral)
        assertFalse(PictureSettings(colorBlind = ColorBlindType.PROTANOPIA).isNeutral)
    }

    @Test fun `tone amount alone does not break neutrality`() = assertTrue(PictureSettings(toneAmount = 1f).isNeutral)

    @Test fun `sanitize clamps to ranges`() {
        val s = PictureSettings(brightness = 9f, contrast = -9f, saturation = 3f, gamma = 0f, sharpness = 5f, vivid = 2f, toneAmount = -1f, sharpNoiseThreshold = 1f).sanitized()
        assertEquals(PictureSettings.BRIGHTNESS_LIMIT, s.brightness, 0f)
        assertEquals(-PictureSettings.CONTRAST_LIMIT, s.contrast, 0f)
        assertEquals(1f, s.saturation, 0f)
        assertEquals(PictureSettings.MIN_GAMMA, s.gamma, 0f)
        assertEquals(PictureSettings.MAX_SHARPNESS, s.sharpness, 0f)
        assertEquals(1f, s.vivid, 0f)
        assertEquals(0f, s.toneAmount, 0f)
        assertEquals(PictureSettings.MAX_NOISE_THRESHOLD, s.sharpNoiseThreshold, 0f)
    }

    @Test fun `encode decode round trip for every preset`() {
        for (p in PicturePreset.values()) assertEquals(p.name, p.settings, PictureSettings.decode(p.settings.encode()))
        val custom = PictureSettings(0.1f, -0.2f, 0.3f, 1.4f, 0.9f, 0.05f, SharpTaps.T25, 0.6f, Tone.COOL, 0.8f, ColorBlindType.TRITANOPIA, ColorBlindMode.SIMULATE)
        assertEquals(custom, PictureSettings.decode(custom.encode()))
    }

    @Test fun `broken text decodes to defaults`() {
        assertEquals(PictureSettings(), PictureSettings.decode(null))
        assertEquals(PictureSettings(), PictureSettings.decode(""))
        assertEquals(PictureSettings(), PictureSettings.decode("v2;1;2;3"))
        assertEquals(PictureSettings(), PictureSettings.decode("v1;a;b;c;d;e;f;T9;g;NEUTRAL;h;OFF;CORRECT"))
        assertEquals(PictureSettings(), PictureSettings.decode("v1;0;0;0;1;0;0.02;T99;0;NEUTRAL;0.5;OFF;CORRECT"))
    }

    @Test fun `decoded out of range values are clamped`() {
        val d = PictureSettings.decode("v1;9;0;0;1;0;0.02;T9;0;NEUTRAL;0.5;OFF;CORRECT")
        assertEquals(PictureSettings.BRIGHTNESS_LIMIT, d.brightness, 0f)
    }

    @Test fun `presets map to themselves and custom mixes to null`() {
        for (p in PicturePreset.values()) assertEquals(p, PicturePreset.matching(p.settings))
        assertEquals(PicturePreset.CLEAR, PicturePreset.matching(PicturePreset.CLEAR.settings.copy(sharpTaps = SharpTaps.T25)))
        assertNull(PicturePreset.matching(PicturePreset.CLEAR.settings.copy(sharpness = 0.51f)))
    }

    @Test fun `OFF preset is neutral and the others are not`() {
        assertTrue(PicturePreset.OFF.settings.isNeutral)
        PicturePreset.values().filter { it != PicturePreset.OFF }.forEach { assertFalse(it.name, it.settings.isNeutral) }
    }

    @Test fun `every preset is already inside the valid ranges`() {
        for (p in PicturePreset.values()) assertEquals(p.name, p.settings, p.settings.sanitized())
    }
}
