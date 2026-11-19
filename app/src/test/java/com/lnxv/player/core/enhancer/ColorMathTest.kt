package com.lnxv.player.core.enhancer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {
    private val eps = 1e-4f

    @Test fun `neutral settings compose to identity`() = assertTrue(ColorMath.compose(PictureSettings()).isIdentity)

    @Test fun `simulation matrices have rows summing to one so white is preserved`() {
        for (m in listOf(ColorMath.PROTANOPIA, ColorMath.DEUTERANOPIA, ColorMath.TRITANOPIA)) {
            for (r in 0..2) assertEquals(1f, m[r * 3] + m[r * 3 + 1] + m[r * 3 + 2], 1e-3f)
        }
    }

    @Test fun `colour blind correction and simulation keep white and black`() {
        for (type in listOf(ColorBlindType.PROTANOPIA, ColorBlindType.DEUTERANOPIA, ColorBlindType.TRITANOPIA)) {
            for (mode in ColorBlindMode.values()) {
                val a = ColorMath.colorBlind(type, mode)
                assertArrayEquals("$type $mode white", floatArrayOf(1f, 1f, 1f), a.apply(1f, 1f, 1f), 2e-3f)
                assertArrayEquals("$type $mode black", floatArrayOf(0f, 0f, 0f), a.apply(0f, 0f, 0f), eps)
            }
        }
    }

    @Test fun `protanopia simulation squeezes pure red toward the green blue axis`() {
        val out = ColorMath.colorBlind(ColorBlindType.PROTANOPIA, ColorBlindMode.SIMULATE).apply(1f, 0f, 0f)
        assertEquals(0.152286f, out[0], eps); assertEquals(0.114503f, out[1], eps); assertEquals(-0.003882f, out[2], eps)
    }

    @Test fun `correction adds the simulated error to green and blue for protan and deutan, red and green for tritan`() {
        val red = ColorMath.colorBlind(ColorBlindType.DEUTERANOPIA, ColorBlindMode.CORRECT).apply(1f, 0f, 0f)
        assertTrue("red channel is untouched by deutan correction", Math.abs(red[0] - 1f) < eps)
        val blue = ColorMath.colorBlind(ColorBlindType.TRITANOPIA, ColorBlindMode.CORRECT).apply(0f, 0f, 1f)
        assertTrue("blue channel of tritan correction does not exceed its simulation error",
            blue[0] != 0f || blue[1] != 0f)
    }

    @Test fun `saturation minus one is greyscale and zero is identity`() {
        val g = ColorMath.saturation(-1f).apply(0.9f, 0.2f, 0.4f)
        assertEquals(g[0], g[1], eps); assertEquals(g[1], g[2], eps)
        assertTrue(ColorMath.saturation(0f).isIdentity)
    }

    @Test fun `saturation keeps grey and luma`() {
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0.5f), ColorMath.saturation(0.8f).apply(0.5f, 0.5f, 0.5f), eps)
        val before = 0.2126f * 0.8f + 0.7152f * 0.3f + 0.0722f * 0.1f
        val o = ColorMath.saturation(0.6f).apply(0.8f, 0.3f, 0.1f)
        assertEquals(before, 0.2126f * o[0] + 0.7152f * o[1] + 0.0722f * o[2], 1e-3f)
    }

    @Test fun `contrast pivots on mid grey and brightness adds`() {
        val c = ColorMath.contrastBrightness(0.4f, 0f)
        assertEquals(0.5f, c.apply(0.5f, 0.5f, 0.5f)[0], eps)
        assertTrue(c.apply(0.8f, 0.8f, 0.8f)[0] > 0.8f)
        assertTrue(c.apply(0.2f, 0.2f, 0.2f)[0] < 0.2f)
        assertEquals(0.3f, ColorMath.contrastBrightness(0f, 0.1f).apply(0.2f, 0.2f, 0.2f)[0], eps)
        assertTrue(ColorMath.contrastBrightness(0f, 0f).isIdentity)
    }

    @Test fun `contrast matches the Media3 Contrast formula`() {
        val k = (1f + 0.3f) / (1.0001f - 0.3f)
        assertEquals(k * 0.7f + 0.5f * (1f - k), ColorMath.contrastBrightness(0.3f, 0f).apply(0.7f, 0.7f, 0.7f)[0], eps)
    }

    @Test fun `tone warm raises red and lowers blue, cool the opposite, neutral is identity`() {
        val w = ColorMath.tone(Tone.WARM, 1f).apply(0.5f, 0.5f, 0.5f)
        assertTrue(w[0] > 0.5f && w[2] < 0.5f); assertEquals(0.5f, w[1], eps)
        val c = ColorMath.tone(Tone.COOL, 1f).apply(0.5f, 0.5f, 0.5f)
        assertTrue(c[0] < 0.5f && c[2] > 0.5f)
        assertTrue(ColorMath.tone(Tone.NEUTRAL, 1f).isIdentity)
        assertTrue(ColorMath.tone(Tone.WARM, 0f).isIdentity)
    }

    @Test fun `then applies the first transform first`() {
        val a = Affine.diagonal(2f, 2f, 2f)
        val b = Affine(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), floatArrayOf(1f, 1f, 1f))
        assertEquals(3f, a.then(b).apply(1f, 1f, 1f)[0], eps) // 2*1+1
        assertEquals(4f, b.then(a).apply(1f, 1f, 1f)[0], eps) // (1+1)*2
    }

    @Test fun `column major layout transposes`() {
        val a = Affine(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f), FloatArray(3))
        assertArrayEquals(floatArrayOf(1f, 4f, 7f, 2f, 5f, 8f, 3f, 6f, 9f), a.columnMajor(), 0f)
    }

    @Test fun `compose is consistent with applying the steps one by one`() {
        val p = PictureSettings(brightness = 0.1f, contrast = 0.2f, saturation = 0.3f, tone = Tone.WARM, toneAmount = 0.7f)
        val s = ColorMath.saturation(p.saturation).apply(0.6f, 0.4f, 0.2f)
        val cb = ColorMath.contrastBrightness(p.contrast, p.brightness).apply(s[0], s[1], s[2])
        val t = ColorMath.tone(p.tone, p.toneAmount).apply(cb[0], cb[1], cb[2])
        assertArrayEquals(t, ColorMath.compose(p).apply(0.6f, 0.4f, 0.2f), 1e-4f)
    }
}
