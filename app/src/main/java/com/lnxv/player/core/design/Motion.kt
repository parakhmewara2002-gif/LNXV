package com.lnxv.player.core.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Click target with a spring "press" feedback (slight shrink, springs back).
 * Apply it BEFORE the glass surface modifier so the whole card scales together.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.bouncyClickable(pressedScale: Float = 0.985f, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceEffects = LocalReduceEffects.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (!reduceEffects && pressed) pressedScale else 1f,
        animationSpec = if (reduceEffects) androidx.compose.animation.core.tween(0) else spring(
            dampingRatio = 0.92f,
            stiffness = 520f
        ),
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
