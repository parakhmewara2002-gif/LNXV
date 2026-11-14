package com.lnxv.player.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.animation.togetherWith
import coil.request.videoFrameMillis
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import com.lnxv.player.core.design.videoSharedBounds
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.lnxv.player.core.util.SeekMath
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.view.WindowManager
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.launch
import android.widget.Toast
import android.content.Intent
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.media3.ui.CaptionStyleCompat
import android.util.TypedValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenRotation
import kotlin.math.abs
import com.lnxv.player.playback.gestures.GestureType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lnxv.player.LNXVApplication
import com.lnxv.player.core.design.GlassBadge
import com.lnxv.player.core.design.GlassButton
import com.lnxv.player.core.design.GlassIconButton
import com.lnxv.player.core.design.GlassPanel
import com.lnxv.player.core.design.NeonCyan
import com.lnxv.player.core.design.Typography
import com.lnxv.player.core.design.liquidGlassSurface
import com.lnxv.player.core.model.AspectRatioMode
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.playback.gestures.GestureOverlayState
import com.lnxv.player.playback.gestures.PlayerGestureDetector
import com.lnxv.player.playback.pip.PipManager
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoId: Long,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember { context as? Activity }
    val playerManager = remember { (context.applicationContext as LNXVApplication).playerManager }

    val playbackState by viewModel.playbackState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val queueUi by viewModel.queueUi.collectAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val reduceEffects = com.lnxv.player.core.design.LocalReduceEffects.current
    val audioDelayMs by viewModel.audioDelayMs.collectAsState()

    var overlayState by remember { mutableStateOf(GestureOverlayState()) }
    var isMoreMenuOpen by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var orientationMode by remember { mutableIntStateOf(0) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val prefs by viewModel.userPrefs.collectAsState()
    val reportScope = rememberCoroutineScope()
    var glassBackdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsState()
    val hasExternalSubtitle by viewModel.hasExternalSubtitle.collectAsState()
    val subtitleFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadExternalSubtitle(uri)
    }

    LaunchedEffect(videoId) {
        viewModel.loadVideo(videoId)
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(uiState.isControlsVisible, playbackState.isPlaying) {
        if (uiState.isControlsVisible && playbackState.isPlaying && !playbackState.isScreenLocked) {
            delay(4000L)
            viewModel.setControlsVisibility(false)
        }
    }

    // Closing the player (UI back button OR system back) saves progress and STOPS playback
    BackHandler {
        viewModel.closePlayer()
        onBackClick()
    }

    // Real immersive fullscreen: hide system bars, keep screen on, draw into display cutout.
    // Re-applied on every resume so bars cannot stay visible after dialogs / returning from PiP.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(activity, lifecycleOwner) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        fun enterImmersive() {
            if (window == null || controller == null) return
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            }
        }
        enterImmersive()
        val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) enterImmersive()
        }
        window?.decorView?.viewTreeObserver?.addOnWindowFocusChangeListener(focusListener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enterImmersive()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val vto = window?.decorView?.viewTreeObserver
            if (vto != null && vto.isAlive) vto.removeOnWindowFocusChangeListener(focusListener)
            if (window != null && controller != null) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE // undo swipe-brightness
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
                window.attributes = lp
            }
        }
    }

    DisposableEffect(Unit) {
        viewModel.setVideoScreenActive(true)
        onDispose {
            viewModel.setVideoScreenActive(false)
            viewModel.saveProgress()
            // Reset orientation to sensor
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val aspectValue: Float? = when (playbackState.aspectRatio) {
        AspectRatioMode.RATIO_16_9 -> 16f / 9f
        AspectRatioMode.RATIO_4_3 -> 4f / 3f
        AspectRatioMode.RATIO_21_9 -> 21f / 9f
        AspectRatioMode.RATIO_18_9 -> 2f
        AspectRatioMode.RATIO_235_1 -> 2.35f
        else -> null
    }
    val surfaceModifier = (if (aspectValue != null) Modifier.aspectRatio(aspectValue) else Modifier.fillMaxSize())
        .graphicsLayer {
            scaleX = overlayState.zoomScale
            scaleY = overlayState.zoomScale
        }

    Box(
        modifier = Modifier
            .videoSharedBounds(videoId)
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Player Surface View
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerManager.exoPlayer
                    useController = false
                    playerViewRef = this
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = when (playbackState.aspectRatio) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                }
            },
            update = { playerView ->
                playerView.resizeMode = when (playbackState.aspectRatio) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                playerView.subtitleView?.let { sv ->
                    sv.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.subtitleTextSize)
                    val fg = try {
                        android.graphics.Color.parseColor(prefs.subtitleTextColorHex)
                    } catch (e: Exception) {
                        android.graphics.Color.WHITE
                    }
                    sv.setStyle(
                        CaptionStyleCompat(
                            fg,
                            0x80000000.toInt(),
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                }
            },
            modifier = surfaceModifier
        )
        }

        // Gesture detector wrapper
        PlayerGestureDetector(
            modifier = Modifier.fillMaxSize(),
            isLocked = playbackState.isScreenLocked,
            durationMs = playbackState.durationMs,
            currentPositionProvider = { viewModel.positionMs.value },
            brightnessEnabled = prefs.isBrightnessGestureEnabled,
            volumeEnabled = prefs.isBrightnessGestureEnabled,
            seekEnabled = prefs.isSeekGestureEnabled,
            longPressEnabled = prefs.isLongPressSpeedEnabled,
            doubleTapSeekMs = prefs.doubleTapSeekSeconds * 1000L,
            onSingleTap = { viewModel.toggleControlsVisibility() },
            onDoubleTapSeek = { delta ->
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                viewModel.seekRelative(delta)
            },
            onDoubleTapCenter = { viewModel.togglePlayPause() },
            onLongPressStart = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                viewModel.startSpeedPreview()
            },
            onLongPressEnd = { viewModel.stopSpeedPreview() },
            onSeekCommitted = { target -> viewModel.seekTo(target) },
            onOverlayStateChanged = { overlayState = it }
        ) {
            // Gesture HUD (brightness / volume / seek / zoom)
            if (overlayState.activeGesture != GestureType.NONE) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xB3000000), RoundedCornerShape(16.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = when (overlayState.activeGesture) {
                                GestureType.BRIGHTNESS -> "Brightness ${overlayState.brightnessPercent}%"
                                GestureType.VOLUME -> "Volume ${overlayState.volumePercent}%"
                                GestureType.SEEK -> {
                                    val d = overlayState.seekDeltaMs
                                    "${if (d >= 0) "+" else "-"}${abs(d) / 1000}s  •  ${VideoItem.formatDuration(overlayState.seekTargetMs)}"
                                }
                                GestureType.ZOOM -> "Zoom ${"%.1f".format(overlayState.zoomScale)}x"
                                else -> ""
                            },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Buffering state: glass disc that fades in/out instead of popping
            AnimatedVisibility(
                visible = playbackState.isBuffering,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0x66101622))
                            .border(1.dp, Color(0x40FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = NeonCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Controls Overlay
            AnimatedVisibility(
                visible = uiState.isControlsVisible || playbackState.isScreenLocked,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160))
            ) {
                if (playbackState.isScreenLocked) {
                    // Only display unlock button when locked
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        GlassIconButton(
                            icon = Icons.Default.Lock,
                            contentDescription = "Unlock Screen",
                            onClick = { viewModel.toggleScreenLock() },
                            tint = NeonCyan,
                            size = 54.dp
                        )
                    }
                } else {
                    // Full Liquid Glass Controls
                    Box(modifier = Modifier.fillMaxSize()) {

                        // Top Gradient & Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xE6000000), Color.Transparent)
                                    )
                                )
                                .padding(top = 36.dp, bottom = 20.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    GlassIconButton(
                                        icon = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        onClick = {
                                            viewModel.closePlayer()
                                            onBackClick()
                                        },
                                        size = 38.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = uiState.currentVideo?.title ?: "Video Playback",
                                        color = Color.White,
                                        style = Typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Aspect Ratio Toggle
                                    GlassIconButton(
                                        icon = Icons.Default.AspectRatio,
                                        contentDescription = "Aspect Ratio",
                                        onClick = { viewModel.showAspectRatioDialog(true) },
                                        size = 36.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Audio Track Switcher
                                    GlassIconButton(
                                        icon = Icons.Default.Audiotrack,
                                        contentDescription = "Audio Tracks",
                                        onClick = { viewModel.showAudioDialog(true) },
                                        size = 36.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Subtitles Switcher
                                    GlassIconButton(
                                        icon = Icons.Default.Subtitles,
                                        contentDescription = "Subtitles",
                                        onClick = { viewModel.showSubtitleDialog(true) },
                                        size = 36.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Picture-in-Picture
                                    if (activity != null && PipManager.isPipSupported(activity)) {
                                        GlassIconButton(
                                            icon = Icons.Default.PictureInPicture,
                                            contentDescription = "PiP",
                                            onClick = {
                                                PipManager.enterPipMode(
                                                    activity,
                                                    uiState.currentVideo?.width ?: 16,
                                                    uiState.currentVideo?.height ?: 9
                                                )
                                            },
                                            size = 36.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    // Overflow Menu
                                    Box {
                                        GlassIconButton(
                                            icon = Icons.Default.MoreVert,
                                            contentDescription = "More",
                                            onClick = { isMoreMenuOpen = true },
                                            size = 36.dp
                                        )
                                    }
                                }
                            }
                        }

                        // Center Play/Pause & Jump Controls
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .liquidGlassSurface(
                                    shape = RoundedCornerShape(32.dp),
                                    backgroundColor = Color(0x331E293B),
                                    borderColor = Color(0x33FFFFFF)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (queueUi.size > 1) 10.dp else 20.dp)
                        ) {
                            if (queueUi.size > 1) {
                                GlassIconButton(
                                    icon = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous video",
                                    onClick = { viewModel.playPrevious() },
                                    size = 40.dp
                                )
                            }
                            GlassIconButton(
                                icon = Icons.Default.FastRewind,
                                contentDescription = "Seek -10s",
                                onClick = { viewModel.seekRelative(-10000L) },
                                size = 44.dp
                            )

                            // Main Play/Pause Button
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan)
                                    .clickable { viewModel.togglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.animation.AnimatedContent(
                                    targetState = playbackState.isPlaying,
                                    transitionSpec = {
                                        if (reduceEffects) {
                                            androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                                        } else {
                                            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)) +
                                                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(160), initialScale = 0.5f)) togetherWith
                                                (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) +
                                                    androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(120), targetScale = 0.5f))
                                        }
                                    },
                                    label = "play_pause_morph"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.Black,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            GlassIconButton(
                                icon = Icons.Default.FastForward,
                                contentDescription = "Seek +10s",
                                onClick = { viewModel.seekRelative(10000L) },
                                size = 44.dp
                            )
                            if (queueUi.size > 1) {
                                GlassIconButton(
                                    icon = Icons.Default.SkipNext,
                                    contentDescription = "Next video",
                                    onClick = { viewModel.playNext() },
                                    size = 40.dp
                                )
                            }
                        }

                        // Bottom Gradient & Timeline HUD
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xEE000000))
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 20.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Timeline Scrubber
                                val positionMs by viewModel.positionMs.collectAsState()
                                var dragFraction by remember { mutableStateOf<Float?>(null) }
                                // Thumbnail preview while scrubbing (local content:// / file:// videos only; never vault/network).
                                val previewVideo = uiState.currentVideo
                                val previewFraction = dragFraction
                                if (previewFraction != null && !reduceEffects && previewVideo != null && !previewVideo.isVault &&
                                    (previewVideo.uri.scheme == "content" || previewVideo.uri.scheme == "file")
                                ) {
                                    val previewMs = (SeekMath.fractionToPosition(previewFraction, playbackState.durationMs) / 1000L) * 1000L
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        coil.compose.AsyncImage(
                                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                .data(previewVideo.uri)
                                                .videoFrameMillis(previewMs)
                                                .size(coil.size.Size(320, 180))
                                                .build(),
                                            contentDescription = null,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = 160.dp, height = 90.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0x66000000))
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                val sliderInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isScrubbing by sliderInteraction.collectIsDraggedAsState()
                                val thumbScale by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = if (isScrubbing && !reduceEffects) 1.5f else 1f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
                                    ),
                                    label = "thumb_scale"
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = VideoItem.formatDuration(
                                            dragFraction?.let { SeekMath.fractionToPosition(it, playbackState.durationMs) } ?: positionMs
                                        ),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Slider(
                                        value = dragFraction ?: SeekMath.positionToFraction(positionMs, playbackState.durationMs),
                                        enabled = playbackState.durationMs > 0 && playbackState.isSeekable,
                                        onValueChange = { dragFraction = it },
                                        interactionSource = sliderInteraction,
                                        thumb = {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .graphicsLayer {
                                                        scaleX = thumbScale
                                                        scaleY = thumbScale
                                                    }
                                                    .background(NeonCyan, CircleShape)
                                            )
                                        },
                                        onValueChangeFinished = {
                                            dragFraction?.let { f ->
                                                viewModel.seekTo(SeekMath.fractionToPosition(f, playbackState.durationMs))
                                            }
                                            dragFraction = null
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = NeonCyan,
                                            activeTrackColor = NeonCyan,
                                            inactiveTrackColor = Color(0x40FFFFFF)
                                        )
                                    )
                                    Text(
                                        text = VideoItem.formatDuration(playbackState.durationMs),
                                        color = Color(0x99FFFFFF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick Utility Controls Bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Speed button
                                        GlassButton(
                                            text = "${playbackState.playbackSpeed}x",
                                            icon = Icons.Default.Speed,
                                            onClick = { viewModel.showSpeedDialog(true) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        // Sleep timer indicator
                                        playbackState.sleepTimerRemainingSeconds?.let { sec ->
                                            GlassBadge(
                                                text = "Timer: ${sec / 60}m",
                                                color = Color(0xFFFFAB00)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Screen Lock
                                        GlassIconButton(
                                            icon = Icons.Default.LockOpen,
                                            contentDescription = "Lock Controls",
                                            onClick = { viewModel.toggleScreenLock() },
                                            size = 36.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        // Fullscreen orientation toggle
                                        GlassIconButton(
                                            icon = when (orientationMode) {
                                                1 -> Icons.Default.ScreenLockLandscape
                                                2 -> Icons.Default.ScreenLockPortrait
                                                else -> Icons.Default.ScreenRotation
                                            },
                                            contentDescription = "Orientation",
                                            onClick = {
                                                orientationMode = (orientationMode + 1) % 3
                                                activity?.requestedOrientation = when (orientationMode) {
                                                    1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                    2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                                }
                                            },
                                            size = 36.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Resume prompt + sleep timer
        uiState.resumePromptPositionMs?.let { pos ->
            ResumeDialog(
                positionMs = pos,
                onResume = { viewModel.resumeFromPrompt() },
                onStartOver = { viewModel.startOverFromPrompt() }
            )
        }
        if (showSleepDialog) {
            SleepTimerDialog(
                activeSeconds = playbackState.sleepTimerRemainingSeconds,
                showEndOfVideo = viewModel.userPrefs.collectAsState().value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.SLEEP_TIMER_EXTRAS),
                onSelect = { viewModel.startSleepTimer(it) },
                onCancelTimer = { viewModel.cancelSleepTimer() },
                onDismiss = { showSleepDialog = false }
            )
        }

        // Frosted-glass backdrop: snapshot of the video taken whenever a panel / menu opens
        val anyPanelOpen = uiState.showSpeedDialog || uiState.showAspectRatioDialog || uiState.showAudioDialog ||
            uiState.showSubtitleDialog || uiState.showPropertiesDialog || isMoreMenuOpen
        LaunchedEffect(anyPanelOpen) {
            if (anyPanelOpen) {
                val view = playerViewRef
                if (view != null) {
                    BackdropCapture.capture(view) { bmp -> glassBackdrop = bmp?.asImageBitmap() }
                }
            } else {
                glassBackdrop = null
            }
        }

        // Side panels: drawn inside the player (no extra window) so fullscreen is never interrupted
        PlayerSidePanel(
            visible = uiState.showSpeedDialog,
            title = "Playback Speed",
            icon = Icons.Default.Speed,
            onDismiss = { viewModel.showSpeedDialog(false) },
            backdrop = glassBackdrop
        ) {
            SpeedBottomSheet(
                currentSpeed = playbackState.playbackSpeed,
                onSpeedSelected = { speed ->
                    viewModel.setPlaybackSpeed(speed)
                    viewModel.showSpeedDialog(false)
                }
            )
        }

        PlayerSidePanel(
            visible = uiState.showAspectRatioDialog,
            title = "Aspect Ratio",
            icon = Icons.Default.AspectRatio,
            onDismiss = { viewModel.showAspectRatioDialog(false) },
            backdrop = glassBackdrop
        ) {
            AspectRatioBottomSheet(
                currentMode = playbackState.aspectRatio,
                onModeSelected = { mode ->
                    viewModel.setAspectRatio(mode)
                    viewModel.showAspectRatioDialog(false)
                }
            )
        }

        PlayerSidePanel(
            visible = uiState.showAudioDialog,
            title = "Audio Tracks (${audioTracks.size})",
            icon = Icons.Default.Audiotrack,
            onDismiss = { viewModel.showAudioDialog(false) },
            backdrop = glassBackdrop
        ) {
            AudioTrackBottomSheet(
                tracks = audioTracks,
                onTrackSelected = { track ->
                    viewModel.selectAudioTrack(track)
                    viewModel.showAudioDialog(false)
                }
            )
        }

        PlayerSidePanel(
            visible = uiState.showSubtitleDialog,
            title = "Subtitles",
            icon = Icons.Default.Subtitles,
            onDismiss = { viewModel.showSubtitleDialog(false) },
            backdrop = glassBackdrop
        ) {
            SubtitleBottomSheet(
                tracks = subtitleTracks,
                onTrackSelected = { track ->
                    viewModel.selectSubtitleTrack(track)
                    viewModel.showSubtitleDialog(false)
                },
                onLoadFile = { subtitleFileLauncher.launch(arrayOf("*/*")) },
                delayMs = subtitleDelayMs,
                canDelay = hasExternalSubtitle,
                onDelayChange = { viewModel.adjustSubtitleDelay(it) }
            )
        }

        PlayerSidePanel(
            visible = uiState.showPropertiesDialog,
            title = "Video Properties",
            icon = Icons.Default.Info,
            onDismiss = { viewModel.showPropertiesDialog(false) },
            backdrop = glassBackdrop
        ) {
            uiState.currentVideo?.let { video ->
                PropertiesBottomSheet(
                    video = video,
                    streamInfo = viewModel.describeStream(),
                    onCopyReport = {
                        reportScope.launch {
                            val report = viewModel.buildDiagnosticsReport()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("LNXV diagnostic report", report))
                            Toast.makeText(context, "Report copied. Paste it in the chat.", Toast.LENGTH_LONG).show()
                        }
                    },
                    onShareReport = {
                        reportScope.launch {
                            val report = viewModel.buildDiagnosticsReport()
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "LNXV diagnostic report")
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(
                                Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }
        }

        // Overflow menu (drawn inside the player, scrollable)
        PlayerOverflowMenu(
            visible = isMoreMenuOpen,
            onDismiss = { isMoreMenuOpen = false },
            backdrop = glassBackdrop
        ) {
                                            PlayerMenuItem(
                                                text = { Text(if (playbackState.sleepTimerRemainingSeconds != null) "Sleep Timer (active)" else "Sleep Timer") },
                                                onClick = {
                                                    showSleepDialog = true
                                                    isMoreMenuOpen = false
                                                }
                                            )
                                            if (queueUi.size > 1) {
                                                PlayerMenuItem(
                                                    text = { Text(if (queueUi.shuffle) "Shuffle: on" else "Shuffle: off") },
                                                    onClick = { viewModel.toggleShuffle() }
                                                )
                                                PlayerMenuItem(
                                                    text = {
                                                        Text(
                                                            when (queueUi.repeat) {
                                                                com.lnxv.player.core.model.RepeatMode.OFF -> "Repeat: off"
                                                                com.lnxv.player.core.model.RepeatMode.ONE -> "Repeat: one"
                                                                com.lnxv.player.core.model.RepeatMode.ALL -> "Repeat: all"
                                                            }
                                                        )
                                                    },
                                                    onClick = { viewModel.cycleRepeat() }
                                                )
                                            }
                                            PlayerMenuItem(
                                                text = { Text("Audio delay +50 ms (now $audioDelayMs ms)") },
                                                onClick = { viewModel.adjustAudioDelay(50L) }
                                            )
                                            PlayerMenuItem(
                                                text = { Text("Audio delay -50 ms") },
                                                onClick = { viewModel.adjustAudioDelay(-50L) }
                                            )
                                            PlayerMenuItem(
                                                text = { Text("Previous frame") },
                                                onClick = {
                                                    viewModel.stepFrame(false)
                                                    isMoreMenuOpen = false
                                                }
                                            )
                                            PlayerMenuItem(
                                                text = { Text("Next frame") },
                                                onClick = {
                                                    viewModel.stepFrame(true)
                                                    isMoreMenuOpen = false
                                                }
                                            )
                                            PlayerMenuItem(
                                                text = { Text("Screenshot") },
                                                onClick = {
                                                    isMoreMenuOpen = false
                                                    playerViewRef?.let { ScreenshotHelper.capture(context, it) }
                                                }
                                            )
                                            PlayerMenuItem(
                                                text = { Text("Set Point A (AB Repeat)") },
                                                onClick = {
                                                    viewModel.setPointA()
                                                    isMoreMenuOpen = false
                                                }
                                            )
                                            PlayerMenuItem(
                                                text = { Text("Set Point B (AB Repeat)") },
                                                onClick = {
                                                    viewModel.setPointB()
                                                    isMoreMenuOpen = false
                                                }
                                            )
                                            PlayerMenuItem(
                                                text = { Text("Clear AB Repeat") },
                                                onClick = {
                                                    viewModel.clearABRepeat()
                                                    isMoreMenuOpen = false
                                                }
                                            )
                                            PlayerMenuItem(
                                                text = { Text("Video Properties") },
                                                onClick = {
                                                    viewModel.showPropertiesDialog(true)
                                                    isMoreMenuOpen = false
                                                }
                                            )
        }

    }
}

