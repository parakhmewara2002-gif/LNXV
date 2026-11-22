package com.lnxv.player.ui.player

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import com.lnxv.player.core.design.icons.AspectRatio
import com.lnxv.player.core.design.icons.ForwardArrow
import com.lnxv.player.core.design.icons.GraphicEq
import com.lnxv.player.core.design.icons.MoreHoriz
import com.lnxv.player.core.design.icons.Pause
import com.lnxv.player.core.design.icons.PictureInPicture
import com.lnxv.player.core.design.icons.RewindArrow
import com.lnxv.player.core.design.icons.SkipNext
import com.lnxv.player.core.design.icons.SkipPrevious
import com.lnxv.player.core.design.icons.Speed
import com.lnxv.player.core.design.icons.Subtitles
import com.lnxv.player.core.design.icons.VolumeOff
import com.lnxv.player.core.design.icons.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.videoFrameMillis
import com.lnxv.player.R
import com.lnxv.player.core.design.bouncyClickable
import com.lnxv.player.core.design.liquidGlassSurface
import com.lnxv.player.core.model.PlaybackState
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.core.util.SeekMath
import com.lnxv.player.playback.pip.PipManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val PillFill = Color(0x552A2F38)
private val PillBorder = Color(0x1FFFFFFF)
private val SoftWhite = Color(0xB3FFFFFF)

/**
 * iOS-style player controls: glass icon pills (top-left), volume pill (top-right), big centre
 * rewind / play-pause / forward row, title + thin progress bar + text chips (bottom-left) and a
 * speed / audio / subtitle pill (bottom-right). All actions map to the existing player features.
 */
@Composable
fun IosPlayerControls(
    viewModel: PlayerViewModel,
    uiState: PlayerUiState,
    playbackState: PlaybackState,
    queueUi: QueueUi,
    activity: Activity?,
    reduceEffects: Boolean,
    onClose: () -> Unit,
    onMore: () -> Unit,
    onSleepTimer: () -> Unit,
    onCycleOrientation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Very soft scrims so the video stays clean (the glass pills carry the contrast).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
        )

        // ---- Top row ----
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(start = 24.dp, end = 24.dp, top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassPill {
                    PillIcon(Icons.Default.Close, stringResource(R.string.cd_back), onClick = onClose)
                }
                GlassPill {
                    if (activity != null && PipManager.isPipSupported(activity)) {
                        PillIcon(Icons.Default.PictureInPicture, stringResource(R.string.cd_pip), onClick = {
                            PipManager.enterPipMode(
                                activity,
                                uiState.currentVideo?.width ?: 16,
                                uiState.currentVideo?.height ?: 9
                            )
                        })
                    }
                    PillIcon(Icons.Default.AspectRatio, stringResource(R.string.cd_aspect), onClick = { viewModel.showAspectRatioDialog(true) })
                    PillIcon(Icons.Default.MoreHoriz, stringResource(R.string.cd_more), onClick = onMore)
                }
            }
            Box(modifier = Modifier.weight(1f).padding(start = 10.dp), contentAlignment = Alignment.CenterEnd) {
                VolumePill(Modifier.widthIn(max = 206.dp))
            }
        }

        // ---- Centre: rewind / play-pause / forward ----
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (queueUi.size > 1) {
                GlassCircle(Icons.Default.SkipPrevious, stringResource(R.string.cd_prev_video), 44.dp) { viewModel.playPrevious() }
            }
            GlassCircle(Icons.Default.RewindArrow, stringResource(R.string.cd_seek_back), 56.dp, label = "10") { viewModel.seekRelative(-10000L) }

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .bouncyClickable(pressedScale = 0.86f) { viewModel.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                if (playbackState.isBuffering) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = com.lnxv.player.core.design.NeonCyan,
                        strokeWidth = 3.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AnimatedContent(
                    targetState = playbackState.isPlaying,
                    transitionSpec = {
                        if (reduceEffects) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (fadeIn(tween(140)) + scaleIn(tween(160), initialScale = 0.6f)) togetherWith
                                (fadeOut(tween(90)) + scaleOut(tween(120), targetScale = 0.6f))
                        }
                    },
                    label = "play_pause_morph"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            GlassCircle(Icons.Default.ForwardArrow, stringResource(R.string.cd_seek_fwd), 56.dp, label = "10") { viewModel.seekRelative(10000L) }
            if (queueUi.size > 1) {
                GlassCircle(Icons.Default.SkipNext, stringResource(R.string.cd_next_video), 44.dp) { viewModel.playNext() }
            }
        }

        // ---- Bottom block ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val video = uiState.currentVideo
            val folder = video?.folderName
            if (!folder.isNullOrBlank()) {
                Text(folder, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = video?.title ?: "Video Playback",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))

            ScrubSection(viewModel, playbackState, video, reduceEffects)

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassChip("Info") { viewModel.showPropertiesDialog(true) }
                    GlassChip("Lock") { viewModel.toggleScreenLock() }
                    GlassChip("Rotate") { onCycleOrientation() }
                    GlassChip(playbackState.sleepTimerRemainingSeconds?.let { "Timer ${it / 60}m" } ?: "Timer") { onSleepTimer() }
                }
                Spacer(modifier = Modifier.width(8.dp))
                GlassPill {
                    val speedLabel = "${playbackState.playbackSpeed}x"
                    PillIcon(Icons.Default.Speed, "Playback speed", onClick = { viewModel.showSpeedDialog(true) })
                    if (speedLabel != "1.0x" && speedLabel != "1x") {
                        Text(speedLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp))
                    }
                    PillIcon(Icons.Default.GraphicEq, stringResource(R.string.cd_audio_tracks), onClick = { viewModel.showAudioDialog(true) })
                    PillIcon(Icons.Default.Subtitles, stringResource(R.string.cd_subtitles), onClick = { viewModel.showSubtitleDialog(true) })
                }
            }
        }
    }
}

/** Reads the 250 ms playback position here only, so just the seek row recomposes (not the whole control overlay). */
@Composable
private fun ScrubSection(
    viewModel: PlayerViewModel,
    playbackState: PlaybackState,
    video: VideoItem?,
    reduceEffects: Boolean
) {
    val positionMs by viewModel.positionMs.collectAsState()
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = playbackState.durationMs
    val shownMs = dragFraction?.let { SeekMath.fractionToPosition(it, duration) } ?: positionMs

    // Thumbnail preview while scrubbing (local content:// / file:// videos only; never vault/network).
    val context = LocalContext.current
    val previewEligible = video != null && !video.isVault && (video.uri.scheme == "content" || video.uri.scheme == "file")
    val thumbnailer = remember(video?.uri, previewEligible) {
        if (previewEligible && video != null) ScrubThumbnailer(context.applicationContext, video.uri) else null
    }
    DisposableEffect(thumbnailer) { onDispose { thumbnailer?.release() } }
    val previewFraction = dragFraction
    if (previewFraction != null && thumbnailer != null) {
        val previewMs = SeekMath.fractionToPosition(previewFraction, duration)
        LaunchedEffect(previewMs / 1000L) { thumbnailer.request(previewMs) }
        val bmp by thumbnailer.frame.collectAsState()
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x66000000))
            ) {
                bmp?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(VideoItem.formatDuration(shownMs), color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        ThinTrack(
            fraction = dragFraction ?: SeekMath.positionToFraction(positionMs, duration),
            enabled = duration > 0 && playbackState.isSeekable,
            onChange = {
                dragFraction = it
                viewModel.scrubTo(SeekMath.fractionToPosition(it, duration)) // no-op unless a drag began (keyframe preview seek)
            },
            onScrubStart = { viewModel.beginScrub() },
            onFinished = {
                viewModel.endScrub() // switch back to exact seek parameters before the final seek
                dragFraction?.let { f -> viewModel.seekTo(SeekMath.fractionToPosition(f, duration)) }
                dragFraction = null
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .semantics {
                    contentDescription = "Seek bar"
                    stateDescription = "${VideoItem.formatDuration(positionMs)} of ${VideoItem.formatDuration(duration)}"
                }
        )
        Text(
            text = "-" + VideoItem.formatDuration((duration - shownMs).coerceAtLeast(0L)),
            color = SoftWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Frosted pill container (fully rounded) used for the icon groups. */
@Composable
private fun GlassPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.liquidGlassSurface(shape = CircleShape, backgroundColor = PillFill, borderColor = PillBorder),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun PillIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .bouncyClickable(pressedScale = 0.88f) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun GlassCircle(icon: ImageVector, contentDescription: String, size: Dp, label: String? = null, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .bouncyClickable(pressedScale = 0.86f) { onClick() }
            .size(size)
            .liquidGlassSurface(shape = CircleShape, backgroundColor = PillFill, borderColor = PillBorder),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(size * 0.6f))
        if (label != null) {
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun GlassChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .bouncyClickable(pressedScale = 0.95f) { onClick() }
            .liquidGlassSurface(shape = CircleShape, backgroundColor = PillFill, borderColor = PillBorder)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Slim iOS-style track: tap or drag anywhere on it; it thickens while dragging. */
@Composable
private fun ThinTrack(
    fraction: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onScrubStart: () -> Unit = {}
) {
    var dragging by remember { mutableStateOf(false) }
    val trackHeight by animateDpAsState(if (dragging) 10.dp else 5.dp, label = "track_height")
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    Box(
        modifier = modifier
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    currentOnChange((offset.x / size.width).coerceIn(0f, 1f))
                    currentOnFinished()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        dragging = true
                        currentOnScrubStart()
                        currentOnChange((start.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        dragging = false
                        currentOnFinished()
                    },
                    onDragCancel = {
                        dragging = false
                        currentOnFinished()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        currentOnChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(Color(0x40FFFFFF))
                .drawWithContent {
                    drawContent()
                    val progressWidth = this.size.width * fraction.coerceIn(0f, 1f)
                    if (progressWidth > 0f) {
                        drawRect(
                            color = Color.White,
                            size = Size(progressWidth, this.size.height)
                        )
                    }
                }
        )
    }
}

/** System media-volume pill (reads/writes STREAM_MUSIC, so it stays in sync with the hardware keys and swipe gesture). */
@Composable
private fun VolumePill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var fraction by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat()) }
    var touching by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: android.content.Intent?) {
                if (!touching) {
                    fraction = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat()
                }
            }
        }
        val filter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        try {
            context.registerReceiver(receiver, filter)
        } catch (_: Exception) {}
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!touching) fraction = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat()
            delay(100)
        }
    }
    GlassPill(modifier) {
        Spacer(modifier = Modifier.width(14.dp))
        ThinTrack(
            fraction = fraction,
            onChange = {
                touching = true
                fraction = it
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVol).roundToInt().coerceIn(0, maxVol), 0)
            },
            onFinished = { touching = false },
            modifier = Modifier.weight(1f).widthIn(min = 40.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = if (fraction <= 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            contentDescription = "Volume",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
    }
}
