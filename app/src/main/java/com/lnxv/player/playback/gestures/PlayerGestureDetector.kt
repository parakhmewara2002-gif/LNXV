package com.lnxv.player.playback.gestures

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

enum class GestureType {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK,
    ZOOM
}

data class GestureOverlayState(
    val activeGesture: GestureType = GestureType.NONE,
    val brightnessPercent: Int = 50,
    val volumePercent: Int = 50,
    val seekTargetMs: Long = 0L,
    val seekDeltaMs: Long = 0L,
    val zoomScale: Float = 1.0f
)

@Composable
fun PlayerGestureDetector(
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    durationMs: Long,
    currentPositionProvider: () -> Long,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (deltaMs: Long) -> Unit,
    onDoubleTapCenter: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    onSeekCommitted: (targetMs: Long) -> Unit,
    onOverlayStateChanged: (GestureOverlayState) -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = remember { context as? Activity }

    var overlayState by remember { mutableStateOf(GestureOverlayState()) }
    var currentGesture by remember { mutableStateOf(GestureType.NONE) }

    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var initialPositionOnDrag by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                if (isLocked) {
                    detectTapGestures(
                        onTap = { onSingleTap() }
                    )
                    return@pointerInput
                }

                var longPressed = false
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        if (longPressed) {
                            longPressed = false
                            onLongPressEnd()
                        }
                    },
                    onTap = { onSingleTap() },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        when {
                            offset.x < screenWidth * 0.35f -> onDoubleTapSeek(-10000L)
                            offset.x > screenWidth * 0.65f -> onDoubleTapSeek(10000L)
                            else -> onDoubleTapCenter()
                        }
                    },
                    onLongPress = {
                        longPressed = true
                        onLongPressStart()
                    }
                )
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput

                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1.0f) {
                        val newScale = (overlayState.zoomScale * zoom).coerceIn(1.0f, 4.0f)
                        overlayState = overlayState.copy(
                            activeGesture = GestureType.ZOOM,
                            zoomScale = newScale
                        )
                        onOverlayStateChanged(overlayState)
                    }
                }
            }
    ) {
        content()
    }
}
