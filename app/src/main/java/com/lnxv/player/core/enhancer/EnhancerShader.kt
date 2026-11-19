package com.lnxv.player.core.enhancer

import kotlin.math.exp

/** One blur sample: offset in texels and normalised weight. */
data class SharpTap(val dx: Int, val dy: Int, val weight: Float)

/** Builds the unsharp-mask blur kernels and the single-pass GLSL ES 1.00 fragment shader. Pure string/number work. */
object EnhancerShader {
    const val VERTEX = """#version 100
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""

    /** Gaussian kernels: 9 = 3x3, 13 = 3x3 plus four taps at distance 2, 25 = 5x5. Weights sum to 1. */
    fun kernel(taps: SharpTaps): List<SharpTap> {
        val points = ArrayList<Pair<Int, Int>>()
        val sigma: Float
        when (taps) {
            SharpTaps.T9 -> { for (y in -1..1) for (x in -1..1) points += x to y; sigma = 1.0f }
            SharpTaps.T13 -> {
                for (y in -1..1) for (x in -1..1) points += x to y
                points += listOf(2 to 0, -2 to 0, 0 to 2, 0 to -2); sigma = 1.2f
            }
            SharpTaps.T25 -> { for (y in -2..2) for (x in -2..2) points += x to y; sigma = 1.4f }
        }
        val raw = points.map { (x, y) -> exp(-((x * x + y * y).toFloat()) / (2f * sigma * sigma)) }
        val sum = raw.sum()
        return points.mapIndexed { i, (x, y) -> SharpTap(x, y, raw[i] / sum) }
    }

    private fun f(v: Float): String = "%.6f".format(java.util.Locale.US, v)

    fun fragment(taps: SharpTaps): String {
        val blur = kernel(taps).joinToString("\n") { t ->
            "    blur += ${f(t.weight)} * texture2D(uTexSampler, vTexSamplingCoord + vec2(${f(t.dx.toFloat())}, ${f(t.dy.toFloat())}) * uTexelSize).rgb;"
        }
        return """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uSharpAmount;
uniform float uSharpThreshold;
uniform mat3 uColorMatrix;
uniform vec3 uColorOffset;
uniform float uVivid;
uniform float uInvGamma;
varying vec2 vTexSamplingCoord;
void main() {
  vec3 c = texture2D(uTexSampler, vTexSamplingCoord).rgb;
  if (uSharpAmount > 0.0) {
    vec3 blur = vec3(0.0);
$blur
    vec3 d = c - blur;
    float lumDetail = abs(dot(d, vec3(0.2126, 0.7152, 0.0722)));
    float gate = smoothstep(uSharpThreshold, uSharpThreshold * 2.0 + 0.0001, lumDetail);
    c += d * (uSharpAmount * gate);
  }
  c = uColorMatrix * c + uColorOffset;
  if (uVivid > 0.0) {
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float mask = smoothstep(${f(Vivid.SKIP_BELOW)}, ${f(Vivid.FULL_ABOVE)}, l);
    float lift = uVivid * ${f(Vivid.LIFT_GAIN)} * (1.0 - l) * (1.0 - l) * mask;
    c = c * (1.0 + lift);
    l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    float k = 1.0 + uVivid * ${f(Vivid.SAT_GAIN)} * sat * mask;
    c = vec3(l) + (c - vec3(l)) * k;
  }
  c = clamp(c, 0.0, 1.0);
  if (uInvGamma != 1.0) {
    c = pow(c, vec3(uInvGamma));
  }
  gl_FragColor = vec4(c, 1.0);
}
"""
    }
}

/** Why the enhancer is switched off for the current video. The UI shows the reason instead of silently doing nothing. */
enum class PictureDisabledReason { HDR_SOURCE, PROTECTED_CONTENT, LOW_END_DEVICE, POWER_SAVE }

object PictureGate {
    /** First matching reason, or null when the enhancer may run. Order = importance for the user. */
    fun decide(isHdr: Boolean, isProtected: Boolean, isLowEnd: Boolean, isPowerSave: Boolean): PictureDisabledReason? = when {
        isProtected -> PictureDisabledReason.PROTECTED_CONTENT
        isHdr -> PictureDisabledReason.HDR_SOURCE
        isLowEnd -> PictureDisabledReason.LOW_END_DEVICE
        isPowerSave -> PictureDisabledReason.POWER_SAVE
        else -> null
    }
}
