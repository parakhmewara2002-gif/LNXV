package com.lnxv.player.core.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Container-transform between a video card thumbnail and the player. No-op when "Reduce effects" is on,
 * when no shared-transition scope is available (e.g. previews) or for transient videos (id < 0).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.videoSharedBounds(videoId: Long): Modifier {
    val reduce = LocalReduceEffects.current
    val transition = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedScope.current
    if (reduce || transition == null || visibility == null || videoId < 0) return this
    return with(transition) {
        this@videoSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "video_$videoId"),
            animatedVisibilityScope = visibility
        )
    }
}
