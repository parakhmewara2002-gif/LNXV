package com.lnxv.player.core.enhancer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancerShaderTest {
    @Test fun `tap counts`() {
        assertEquals(9, EnhancerShader.kernel(SharpTaps.T9).size)
        assertEquals(13, EnhancerShader.kernel(SharpTaps.T13).size)
        assertEquals(25, EnhancerShader.kernel(SharpTaps.T25).size)
    }

    @Test fun `weights sum to one and are symmetric and peak at the centre`() {
        for (t in SharpTaps.values()) {
            val k = EnhancerShader.kernel(t)
            assertEquals(t.name, 1f, k.map { it.weight }.sum(), 1e-4f)
            assertEquals(t.name, k.maxByOrNull { it.weight }!!.let { it.dx to it.dy }, 0 to 0)
            for (tap in k) {
                val mirror = k.first { it.dx == -tap.dx && it.dy == -tap.dy }
                assertEquals(tap.weight, mirror.weight, 1e-6f)
            }
        }
    }

    @Test fun `fragment shader samples exactly the kernel taps plus the centre`() {
        for (t in SharpTaps.values()) {
            val src = EnhancerShader.fragment(t)
            val samples = Regex("texture2D").findAll(src).count()
            assertEquals(t.name, t.taps + 1, samples)
        }
    }

    @Test fun `shader declares every uniform the program sets`() {
        val src = EnhancerShader.fragment(SharpTaps.T9)
        listOf("uTexSampler", "uTexelSize", "uSharpAmount", "uSharpThreshold", "uColorMatrix", "uColorOffset", "uVivid", "uInvGamma")
            .forEach { assertTrue(it, src.contains("uniform") && src.contains(it)) }
        assertTrue(src.startsWith("#version 100"))
        assertTrue(EnhancerShader.VERTEX.startsWith("#version 100"))
        assertFalse("no locale dependent decimal commas", Regex("""\d,\d{6}""").containsMatchIn(src))
    }

    @Test fun `vivid constants are shared with the CPU reference`() {
        val src = EnhancerShader.fragment(SharpTaps.T9)
        assertTrue(src.contains("%.6f".format(java.util.Locale.US, Vivid.LIFT_GAIN)))
        assertTrue(src.contains("%.6f".format(java.util.Locale.US, Vivid.SAT_GAIN)))
    }

    /** Writes the shaders to build/shaders so a CI step can run glslangValidator on them. Not an assertion on content. */
    @Test fun `dump shader sources for the CI validator`() {
        val dir = File("build/shaders").apply { mkdirs() }
        File(dir, "enhancer.vert").writeText(EnhancerShader.VERTEX)
        for (t in SharpTaps.values()) File(dir, "enhancer_${t.taps}.frag").writeText(EnhancerShader.fragment(t))
        assertEquals(4, dir.listFiles()!!.size)
    }
}

class VividTest {
    @Test fun `amount zero is a no-op`() = assertEquals(0.4f, Vivid.apply(0.4f, 0.3f, 0.2f, 0f)[0], 0f)

    @Test fun `black and near black are left alone`() {
        val o = Vivid.apply(0f, 0f, 0f, 1f)
        assertEquals(0f, o[0], 0f)
        val dark = Vivid.apply(0.01f, 0.01f, 0.01f, 1f)
        assertEquals(0.01f, dark[0], 1e-4f)
    }

    @Test fun `white is protected`() {
        val o = Vivid.apply(1f, 1f, 1f, 1f)
        assertEquals(1f, o[0], 0.02f)
    }

    @Test fun `mid tones are lifted and more at higher amounts`() {
        val a = Vivid.apply(0.3f, 0.3f, 0.3f, 0.5f)[0]
        val b = Vivid.apply(0.3f, 0.3f, 0.3f, 1f)[0]
        assertTrue(a > 0.3f); assertTrue(b > a)
    }

    @Test fun `lift is stronger in the shadows than in the highlights`() {
        val shadow = Vivid.apply(0.2f, 0.2f, 0.2f, 1f)[0] / 0.2f
        val highlight = Vivid.apply(0.9f, 0.9f, 0.9f, 1f)[0] / 0.9f
        assertTrue(shadow > highlight)
    }

    @Test fun `colourful pixels get more saturation than greys`() {
        val grey = Vivid.apply(0.4f, 0.4f, 0.4f, 1f)
        assertEquals(grey[0], grey[1], 1e-5f)
        val c = Vivid.apply(0.6f, 0.3f, 0.2f, 1f)
        val spreadBefore = 0.6f - 0.2f
        assertTrue(c.max() - c.min() > spreadBefore)
    }
}

class PictureGateTest {
    @Test fun `no reason when nothing applies`() = assertEquals(null, PictureGate.decide(false, false, false, false))
    @Test fun `each condition maps to its reason`() {
        assertEquals(PictureDisabledReason.HDR_SOURCE, PictureGate.decide(true, false, false, false))
        assertEquals(PictureDisabledReason.PROTECTED_CONTENT, PictureGate.decide(false, true, false, false))
        assertEquals(PictureDisabledReason.LOW_END_DEVICE, PictureGate.decide(false, false, true, false))
        assertEquals(PictureDisabledReason.POWER_SAVE, PictureGate.decide(false, false, false, true))
    }
    @Test fun `protected content wins over everything`() =
        assertEquals(PictureDisabledReason.PROTECTED_CONTENT, PictureGate.decide(true, true, true, true))
}
