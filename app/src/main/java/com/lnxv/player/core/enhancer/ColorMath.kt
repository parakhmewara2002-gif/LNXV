package com.lnxv.player.core.enhancer

/**
 * A 3x3 matrix plus an offset: `out = m * in + t`. Stored row-major. Pure math, shared by the GPU uniform setup and the tests.
 */
class Affine(val m: FloatArray, val t: FloatArray) {
    init { require(m.size == 9 && t.size == 3) }

    /** Applies this transform first, then [next]. */
    fun then(next: Affine): Affine {
        val nm = FloatArray(9)
        for (r in 0..2) for (c in 0..2) {
            var s = 0f
            for (k in 0..2) s += next.m[r * 3 + k] * m[k * 3 + c]
            nm[r * 3 + c] = s
        }
        val nt = FloatArray(3)
        for (r in 0..2) nt[r] = next.m[r * 3] * t[0] + next.m[r * 3 + 1] * t[1] + next.m[r * 3 + 2] * t[2] + next.t[r]
        return Affine(nm, nt)
    }

    fun apply(r: Float, g: Float, b: Float): FloatArray = FloatArray(3) { i -> m[i * 3] * r + m[i * 3 + 1] * g + m[i * 3 + 2] * b + t[i] }

    /** Column-major, which is what OpenGL expects for a `mat3` uniform (no transpose). */
    fun columnMajor(): FloatArray = FloatArray(9) { i -> m[(i % 3) * 3 + i / 3] }

    val isIdentity: Boolean
        get() = m.contentEquals(IDENTITY_M) && t.all { it == 0f }

    companion object {
        private val IDENTITY_M = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        fun identity() = Affine(IDENTITY_M.copyOf(), FloatArray(3))
        fun diagonal(r: Float, g: Float, b: Float) = Affine(floatArrayOf(r, 0f, 0f, 0f, g, 0f, 0f, 0f, b), FloatArray(3))
    }
}

/**
 * Colour matrices for the linear part of the Picture pipeline (everything except sharpening, Vivid and gamma, which
 * are non-linear and live in the shader).
 *
 * Colour-vision-deficiency simulation matrices: Machado, Oliveira & Fernandes, "A Physiologically-based Model for
 * Simulation of Color Vision Deficiency", IEEE Transactions on Visualization and Computer Graphics 15(6), 2009
 * (severity 1.0). Correction redistributes the simulated error into the remaining channels as in the daltonization
 * method of Fidaner, Lin & Ozguven, "Analysis of Color Blindness" (Stanford EE368 project, 2005).
 * The matrices were applied to display-referred RGB, an approximation of the paper's linear RGB.
 */
object ColorMath {
    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    val PROTANOPIA = floatArrayOf(0.152286f, 1.052583f, -0.204868f, 0.114503f, 0.786281f, 0.099216f, -0.003882f, -0.048116f, 1.051998f)
    val DEUTERANOPIA = floatArrayOf(0.367322f, 0.860646f, -0.227968f, 0.280085f, 0.672501f, 0.047413f, -0.011820f, 0.042940f, 0.968881f)
    val TRITANOPIA = floatArrayOf(1.255528f, -0.076749f, -0.178779f, -0.078411f, 0.930809f, 0.147602f, 0.004733f, 0.691367f, 0.303900f)

    private val ERROR_TO_GB = floatArrayOf(0f, 0f, 0f, 0.7f, 1f, 0f, 0.7f, 0f, 1f)
    private val ERROR_TO_RG = floatArrayOf(1f, 0f, 0.7f, 0f, 1f, 0.7f, 0f, 0f, 0f)

    /** [s] in [-1, 1]: -1 is greyscale, 0 unchanged, 1 doubles the colour difference from grey. */
    fun saturation(s: Float): Affine {
        val k = 1f + s
        val a = (1f - k)
        return Affine(
            floatArrayOf(
                a * LR + k, a * LG, a * LB,
                a * LR, a * LG + k, a * LB,
                a * LR, a * LG, a * LB + k
            ),
            FloatArray(3)
        )
    }

    /** Contrast about mid-grey using the same curve as Media3's `Contrast`, then a brightness offset. */
    fun contrastBrightness(contrast: Float, brightness: Float): Affine {
        val k = (1f + contrast) / (1.0001f - contrast)
        val off = 0.5f * (1f - k) + brightness
        return Affine(floatArrayOf(k, 0f, 0f, 0f, k, 0f, 0f, 0f, k), floatArrayOf(off, off, off))
    }

    /** Warm lifts red and lowers blue, cool does the opposite. [amount] 0..1 maps to up to +-12% gain. */
    fun tone(tone: Tone, amount: Float): Affine {
        val d = 0.12f * amount
        return when (tone) {
            Tone.NEUTRAL -> Affine.identity()
            Tone.WARM -> Affine.diagonal(1f + d, 1f, 1f - d)
            Tone.COOL -> Affine.diagonal(1f - d, 1f, 1f + d)
        }
    }

    fun colorBlind(type: ColorBlindType, mode: ColorBlindMode): Affine {
        if (type == ColorBlindType.OFF) return Affine.identity()
        val sim = when (type) {
            ColorBlindType.PROTANOPIA -> PROTANOPIA
            ColorBlindType.DEUTERANOPIA -> DEUTERANOPIA
            else -> TRITANOPIA
        }
        if (mode == ColorBlindMode.SIMULATE) return Affine(sim.copyOf(), FloatArray(3))
        val e = if (type == ColorBlindType.TRITANOPIA) ERROR_TO_RG else ERROR_TO_GB
        // corrected = I + E * (I - Sim)
        val out = FloatArray(9)
        for (r in 0..2) for (c in 0..2) {
            var s = if (r == c) 1f else 0f
            for (k in 0..2) s += e[r * 3 + k] * ((if (k == c) 1f else 0f) - sim[k * 3 + c])
            out[r * 3 + c] = s
        }
        return Affine(out, FloatArray(3))
    }

    /** Order: colour-blind assist, saturation, contrast + brightness, tone. */
    fun compose(p: PictureSettings): Affine =
        colorBlind(p.colorBlind, p.colorBlindMode)
            .then(saturation(p.saturation))
            .then(contrastBrightness(p.contrast, p.brightness))
            .then(tone(p.tone, p.toneAmount))
}

/**
 * CPU reference of the "Vivid" curve. The shader is generated from the same constants, so tests on this function pin
 * the behaviour: near-black is skipped, highlights are protected, dark and mid tones are lifted, colourful pixels get
 * a small extra saturation boost.
 */
object Vivid {
    const val LIFT_GAIN = 0.45f
    const val SAT_GAIN = 0.35f
    const val SKIP_BELOW = 0.02f
    const val FULL_ABOVE = 0.15f

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun apply(r: Float, g: Float, b: Float, amount: Float): FloatArray {
        if (amount <= 0f) return floatArrayOf(r, g, b)
        var l = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val mask = smoothstep(SKIP_BELOW, FULL_ABOVE, l)
        val lift = amount * LIFT_GAIN * (1f - l) * (1f - l) * mask
        var cr = r * (1f + lift)
        var cg = g * (1f + lift)
        var cb = b * (1f + lift)
        l = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
        val sat = maxOf(cr, cg, cb) - minOf(cr, cg, cb)
        val k = 1f + amount * SAT_GAIN * sat * mask
        cr = l + (cr - l) * k
        cg = l + (cg - l) * k
        cb = l + (cb - l) * k
        return floatArrayOf(cr, cg, cb)
    }
}
