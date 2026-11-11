package com.lnxv.player.playback.gestures

import androidx.compose.runtime.Immutable
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import com.lnxv.player.core.util.SeekMath
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
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

@Immutable
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
    brightnessEnabled: Boolean = true,
    volumeEnabled: Boolean = true,
    seekEnabled: Boolean = true,
    longPressEnabled: Boolean = true,
    doubleTapSeekMs: Long = 10_000L,
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

    val latestDuration by rememberUpdatedState(durationMs)
    val latestPosition by rememberUpdatedState(currentPositionProvider)
    val latestOverlayCallback by rememberUpdatedState(onOverlayStateChanged)
    val latestSeekCommit by rememberUpdatedState(onSeekCommitted)
    val latestBrightnessEnabled by rememberUpdatedState(brightnessEnabled)
    val latestVolumeEnabled by rememberUpdatedState(volumeEnabled)
    val latestSeekEnabled by rememberUpdatedState(seekEnabled)
    val latestLongPressEnabled by rememberUpdatedState(longPressEnabled)
    val latestDoubleTapMs by rememberUpdatedState(doubleTapSeekMs)

    var overlayState by remember { mutableStateOf(GestureOverlayState()) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                if (isLocked) {
                    detectTapGestures(onTap = { onSingleTap() })
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
                            offset.x < screenWidth * 0.35f -> onDoubleTapSeek(-latestDoubleTapMs)
                            offset.x > screenWidth * 0.65f -> onDoubleTapSeek(latestDoubleTapMs)
                            else -> onDoubleTapCenter()
                        }
                    },
                    onLongPress = {
                        if (latestLongPressEnabled) {
                            longPressed = true
                            onLongPressStart()
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)

                    var mode = GestureType.NONE
                    var pinching = false
                    var ignored = false
                    var accX = 0f
                    var accY = 0f
                    var startPos = 0L
                    var startBrightness = 0.5f
                    var startVolume = 0f
                    var seekTarget = 0L

                    fun publish(state: GestureOverlayState) {
                        overlayState = state
                        latestOverlayCallback(state)
                    }

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }

                        if (pressedCount >= 2) {
                            // Two-finger pinch zoom (1x - 4x)
                            pinching = true
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                zoomScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                publish(
                                    overlayState.copy(
                                        activeGesture = GestureType.ZOOM,
                                        zoomScale = zoomScale
                                    )
                                )
                                event.changes.forEach { it.consume() }
                            }
                        } else if (!pinching) {
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                val delta = change.positionChange()
                                accX += delta.x
                                accY += delta.y

                                if (mode == GestureType.NONE && !ignored &&
                                    (abs(accX) > touchSlop || abs(accY) > touchSlop)
                                ) {
                                    val candidate = when {
                                        abs(accX) > abs(accY) -> GestureType.SEEK
                                        down.position.x < w / 2f -> GestureType.BRIGHTNESS
                                        else -> GestureType.VOLUME
                                    }
                                    val allowed = when (candidate) {
                                        GestureType.SEEK -> latestSeekEnabled
                                        GestureType.BRIGHTNESS -> latestBrightnessEnabled
                                        else -> latestVolumeEnabled
                                    }
                                    if (allowed) mode = candidate else ignored = true
                                    startPos = latestPosition()
                                    val b = activity?.window?.attributes?.screenBrightness ?: -1f
                                    startBrightness = if (b < 0f) 0.5f else b
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                                }

                                when (mode) {
                                    GestureType.SEEK -> {
                                        seekTarget = SeekMath.dragTarget(startPos, accX, w, latestDuration)
                                        publish(
                                            overlayState.copy(
                                                activeGesture = GestureType.SEEK,
                                                seekTargetMs = seekTarget,
                                                seekDeltaMs = seekTarget - startPos
                                            )
                                        )
                                        change.consume()
                                    }
                                    GestureType.BRIGHTNESS -> {
                                        val b = (startBrightness - accY / h * 1.2f).coerceIn(0.02f, 1f)
                                        activity?.window?.let { win ->
                                            val lp = win.attributes
                                            lp.screenBrightness = b
                                            win.attributes = lp
                                        }
                                        publish(
                                            overlayState.copy(
                                                activeGesture = GestureType.BRIGHTNESS,
                                                brightnessPercent = (b * 100).toInt()
                                            )
                                        )
                                        change.consume()
                                    }
                                    GestureType.VOLUME -> {
                                        val frac = (startVolume - accY / h * 1.2f).coerceIn(0f, 1f)
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            (frac * maxVol).toInt().coerceIn(0, maxVol),
                                            0
                                        )
                                        publish(
                                            overlayState.copy(
                                                activeGesture = GestureType.VOLUME,
                                                volumePercent = (frac * 100).toInt()
                                            )
                                        )
                                        change.consume()
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (mode == GestureType.SEEK) latestSeekCommit(seekTarget)
                    if (mode != GestureType.NONE || pinching) {
                        publish(overlayState.copy(activeGesture = GestureType.NONE))
                    }
                }
            }
    ) {
        content()
    }
}
