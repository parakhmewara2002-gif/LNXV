package com.lnxv.player.core.design

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Real backdrop refraction for the few floating glass bars (HIGH tier, API 33+ only).
 *
 * How it works: the scrolling list (the "source") gets a RenderEffect running an AGSL shader on the list's own pixels.
 * The shader receives the bounds of up to two glass bars ("lenses") and, only inside them, re-samples the list with:
 *  - a rounded-box SDF edge band, distortion = strength * edgeAmount^2 (Figma refraction.ts / shaders.ts),
 *  - per-channel chromatic aberration inside that band,
 *  - a frost blur (13-tap rotated disc instead of Figma's 15x15 + 7x7 gaussian, which is far too heavy for a phone GPU).
 * Deviation from the Figma shader: the refraction direction is the SDF gradient (true edge normal) rather than the
 * direction from the shape centre, which is wrong for wide bars.
 * It samples what the list drew, so it never touches the video SurfaceView and costs nothing while no lens is registered.
 * If the shader fails to compile or a uniform call throws, [LiquidLensState.failed] flips and callers fall back to Haze.
 */
@Stable
class LiquidLensState {
    /** Lens bounds in ROOT pixel coordinates; slot 0 = top bar, slot 1 = bottom bar. */
    private val lenses = mutableStateListOf<LensRect?>(null, null)
    var sourceOrigin by mutableStateOf(Offset.Zero)
    var failed by mutableStateOf(false)
        private set

    fun lens(slot: Int): LensRect? = lenses[slot]
    fun set(slot: Int, rect: LensRect?) { if (lenses[slot] != rect) lenses[slot] = rect }
    fun markFailed() { failed = true }
}

data class LensRect(val left: Float, val top: Float, val right: Float, val bottom: Float, val radius: Float)

/** True when this device/profile may use the AGSL lens at all. Pure so callers and tests can share it. */
fun lensSupported(profile: GlassProfile, apiLevel: Int): Boolean = profile.allowsLens && apiLevel >= GlassProfileResolver.LENS_MIN_API

private const val LENS_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float4 uLensA;
uniform float4 uLensB;
uniform float2 uRadius;
uniform float4 uParams;

float sdBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float2 sdNormal(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    if (q.x > 0.0 && q.y > 0.0) { return s * normalize(q); }
    if (q.x > q.y) { return float2(s.x, 0.0); }
    return float2(0.0, s.y);
}

half4 sampleC(float2 c) {
    return content.eval(clamp(c, float2(0.5), uSize - 0.5));
}

half4 frost(float2 c, float rad, float rot) {
    half4 acc = sampleC(c);
    if (rad < 0.5) { return acc; }
    float total = 1.0;
    for (int i = 0; i < 12; i++) {
        float fi = float(i) + 1.0;
        float a = fi * 2.39996323 + rot;
        float r = rad * sqrt(fi / 12.0);
        acc += sampleC(c + float2(cos(a), sin(a)) * r);
        total += 1.0;
    }
    return acc / total;
}

half4 lens(float4 rect, float radius, float2 fc, half4 fallback) {
    if (rect.z <= rect.x) { return fallback; }
    float2 center = (rect.xy + rect.zw) * 0.5;
    float2 hb = (rect.zw - rect.xy) * 0.5;
    float2 p = fc - center;
    float rr = min(radius, min(hb.x, hb.y));
    float sdf = sdBox(p, hb, rr);
    if (sdf > 0.0) { return fallback; }

    float edge = max(uParams.x, 1.0);
    float amt = clamp((sdf + edge) / edge, 0.0, 1.0);
    float dist = uParams.y * amt * amt;
    float2 n = sdNormal(p, hb, rr);
    float rot = 6.2831853 * fract(52.9829189 * fract(dot(fc, float2(0.06711056, 0.00583715))));

    float2 gc = fc - n * dist;
    float ca = uParams.z;
    if (amt > 0.02 && ca > 0.01) {
        float rD = max(0.0, dist - ca * 0.5);
        float bD = dist + ca * 0.5;
        half4 g = frost(gc, uParams.w, rot);
        half4 r = frost(fc - n * rD, uParams.w, rot);
        half4 b = frost(fc - n * bD, uParams.w, rot);
        return half4(r.r, g.g, b.b, g.a);
    }
    return frost(gc, uParams.w, rot);
}

half4 main(float2 fc) {
    half4 c = content.eval(fc);
    c = lens(uLensA, uRadius.x, fc, c);
    c = lens(uLensB, uRadius.y, fc, c);
    return c;
}
"""

@RequiresApi(33)
private class LensShader {
    private val shader = RuntimeShader(LENS_AGSL)

    fun effect(width: Float, height: Float, a: LensRect?, b: LensRect?, origin: Offset, density: Float): RenderEffect {
        shader.setFloatUniform("uSize", width, height)
        setLens("uLensA", a, origin)
        setLens("uLensB", b, origin)
        shader.setFloatUniform("uRadius", a?.radius ?: 0f, b?.radius ?: 0f)
        // edge band, refraction strength, chromatic aberration, frost radius (all dp -> px)
        shader.setFloatUniform("uParams", 14f * density, 16f * density, 2.5f * density, 4.5f * density)
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }

    private fun setLens(name: String, r: LensRect?, origin: Offset) {
        if (r == null) shader.setFloatUniform(name, 0f, 0f, 0f, 0f)
        else shader.setFloatUniform(name, r.left - origin.x, r.top - origin.y, r.right - origin.x, r.bottom - origin.y)
    }
}

/** Put on the scrolling content that sits behind the glass bars. No-op below API 33 or after a shader failure. */
fun Modifier.liquidLensSource(state: LiquidLensState): Modifier = composed {
    if (Build.VERSION.SDK_INT < GlassProfileResolver.LENS_MIN_API || state.failed) return@composed Modifier
    val holder = remember { if (Build.VERSION.SDK_INT >= 33) runCatching { LensShader() }.getOrNull() else null }
    SideEffect { if (holder == null) state.markFailed() }
    if (holder == null) return@composed Modifier
    val density = LocalDensity.current.density
    Modifier
        .onGloballyPositioned { state.sourceOrigin = it.positionInRoot() }
        .graphicsLayer {
            val a = state.lens(0)
            val b = state.lens(1)
            if (a == null && b == null) {
                renderEffect = null
            } else {
                renderEffect = try {
                    if (Build.VERSION.SDK_INT >= 33) {
                        holder.effect(size.width, size.height, a, b, state.sourceOrigin, density).asComposeRenderEffect()
                    } else null
                } catch (t: Throwable) {
                    state.markFailed()
                    null
                }
            }
        }
}

/** Registers this element's bounds as lens [slot]. [bleedTop] extends the lens above the element (for bars that touch the screen edge). */
fun Modifier.liquidLensTarget(state: LiquidLensState, slot: Int, cornerRadius: Dp, bleedTop: Dp = 0.dp, enabled: Boolean = true): Modifier = composed {
    val density = LocalDensity.current
    DisposableEffect(state, slot, enabled) { onDispose { state.set(slot, null) } }
    if (!enabled) return@composed Modifier
    val radiusPx = with(density) { cornerRadius.toPx() }
    val bleedPx = with(density) { bleedTop.toPx() }
    Modifier.onGloballyPositioned { c ->
        val r = c.boundsInRoot()
        state.set(slot, LensRect(r.left, r.top - bleedPx, r.right, r.bottom, radiusPx))
    }
}
