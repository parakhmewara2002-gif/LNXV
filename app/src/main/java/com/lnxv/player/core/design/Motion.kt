package com.lnxv.player.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** System reduce motion or disabled animations token. */
val LocalReduceMotion = compositionLocalOf { false }

/** Centralized iOS-grade motion tokens with calibrated spring physics and easing curves. */
object MotionTokens {
    // Easing curves
    val IosStandardDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val IosStandardAccelerate = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
    val IosEmphasized = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)

    // Springs
    /** Fluid motion for sheets, panels, dialog presentations (damping 0.86, stiffness 380). */
    val SpringFluid = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = 380f
    )

    /** Snappy response for buttons, toggles, chips, HUD indicators (damping 0.88, stiffness 620). */
    val SpringSnappy = spring<Float>(
        dampingRatio = 0.88f,
        stiffness = 620f
    )

    /** Micro-bounce for play/pause pops, favorite hearts, checkmarks (damping 0.74, stiffness 480). */
    val SpringBouncy = spring<Float>(
        dampingRatio = 0.74f,
        stiffness = 480f
    )

    // Durations
    const val FAST_MS = 120
    const val STANDARD_MS = 220
    const val SLOW_MS = 360
    const val DurationQuick = 140
    const val DurationStandard = 240
    const val DurationFluid = 340
    const val DurationSlow = 480

    /**
     * Applies the system "animator duration scale" (Settings > Developer options, or Remove animations in
     * accessibility): 0 means animations are off, so the duration collapses to 0; otherwise it is scaled and clamped
     * to at least 1 ms.
     */
    fun scaled(baseMs: Int, animatorScale: Float): Int {
        if (animatorScale <= 0f || animatorScale.isNaN()) return 0
        return (baseMs * animatorScale).toInt().coerceAtLeast(1)
    }

    /** Returns instant spec if reduce motion is requested, else the provided spec. */
    @Composable
    fun <T> adaptiveSpec(normalSpec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> {
        val reduce = LocalReduceMotion.current || LocalReduceEffects.current
        return if (reduce) snap() else normalSpec
    }
}

/**
 * Click target with a calibrated spring "press" feedback (slight shrink, springs back).
 * When reduce motion is active or system animations are disabled, transitions are instantaneous without jank.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.bouncyClickable(
    pressedScale: Float = 0.985f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current || LocalReduceEffects.current

    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (reduceMotion) snap() else MotionTokens.SpringSnappy,
        label = "precision_press_scale"
    )

    val graphicMod = this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

    return if (onLongClick != null) {
        graphicMod.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onLongClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onLongClick()
            },
            onClick = onClick
        )
    } else {
        graphicMod.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    }
}
