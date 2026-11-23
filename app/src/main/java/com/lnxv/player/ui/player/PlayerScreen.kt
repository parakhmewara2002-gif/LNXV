package com.lnxv.player.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.lnxv.player.core.design.icons.AspectRatio
import com.lnxv.player.core.design.icons.Audiotrack
import com.lnxv.player.core.design.icons.FastForward
import com.lnxv.player.core.design.icons.SkipNext
import com.lnxv.player.core.design.icons.SkipPrevious
import com.lnxv.player.core.design.icons.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import com.lnxv.player.core.design.icons.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import com.lnxv.player.core.design.icons.Pause
import com.lnxv.player.core.design.icons.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import com.lnxv.player.core.design.icons.Speed
import com.lnxv.player.core.design.icons.Subtitles
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import com.lnxv.player.core.design.icons.ScreenLockPortrait
import com.lnxv.player.core.design.icons.ScreenLockLandscape
import com.lnxv.player.core.design.icons.ScreenRotation
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
import com.lnxv.player.core.design.bouncyClickable
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
    var showMultiScreen by remember { mutableStateOf(false) }
    var orientationMode by remember { mutableIntStateOf(0) }
    var videoRotation by remember { mutableIntStateOf(0) }
    var videoFlip by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val prefs by viewModel.userPrefs.collectAsState()
    val reportScope = rememberCoroutineScope()
    var glassBackdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsState()
    val hasExternalSubtitle by viewModel.hasExternalSubtitle.collectAsState()
    val subtitleEncoding by viewModel.subtitleEncoding.collectAsState()
    val subtitleFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadExternalSubtitle(uri)
    }

    LaunchedEffect(videoId) {
        viewModel.loadVideo(videoId)
    }

    DisposableEffect(Unit) {
        com.lnxv.player.playback.popup.PopupWindowController.onClosed = {
            playerViewRef?.let { v -> v.player = null; v.player = playerManager.exoPlayer }
        }
        onDispose { com.lnxv.player.playback.popup.PopupWindowController.onClosed = null }
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

    // Panic Mode / Flip-to-Hide: turning phone face down pauses, mutes, closes player instantly
    com.lnxv.player.playback.gestures.FlipToHideEffect(enabled = true) {
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
            // Rotation/flip (Phase 5, flag VIDEO_TRANSFORMS). At 90/270 degrees shrink so the frame still fits.
            val quarter = videoRotation % 180 != 0
            val fit = if (quarter && size.width > 0f && size.height > 0f) minOf(size.width / size.height, size.height / size.width) else 1f
            rotationZ = videoRotation.toFloat()
            scaleX = overlayState.zoomScale * fit * (if (videoFlip) -1f else 1f)
            scaleY = overlayState.zoomScale * fit
        }

    val keyFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .videoSharedBounds(videoId)
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(keyFocus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val action = com.lnxv.player.playback.gestures.PlayerKeyMap.resolve(ev.nativeKeyEvent.keyCode, ev.isShiftPressed)
                    ?: return@onPreviewKeyEvent false
                when (action) {
                    com.lnxv.player.playback.gestures.KeyAction.TOGGLE_PLAY -> viewModel.togglePlayPause()
                    com.lnxv.player.playback.gestures.KeyAction.NEXT -> viewModel.playNext()
                    com.lnxv.player.playback.gestures.KeyAction.PREVIOUS -> viewModel.playPrevious()
                    com.lnxv.player.playback.gestures.KeyAction.SHOW_CONTROLS -> viewModel.setControlsVisibility(true)
                    else -> viewModel.seekRelative(com.lnxv.player.playback.gestures.PlayerKeyMap.seekDeltaMs(action))
                }
                true
            }
    ) {
        // Player Surface View
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val curVideo = uiState.currentVideo
            val isYouTubeWeb = curVideo?.mimeType == "video/youtube" || (curVideo?.path != null && com.lnxv.player.core.youtube.YouTubeClient.isYouTubeUrl(curVideo.path)) || (curVideo?.uri != null && com.lnxv.player.core.youtube.YouTubeClient.isYouTubeUrl(curVideo.uri.toString()))
            if (isYouTubeWeb && curVideo != null) {
                val yId = com.lnxv.player.core.youtube.YouTubeClient.extractVideoId(curVideo.path.ifEmpty { curVideo.uri.toString() }) ?: ""
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 11; TECNO CG7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            webChromeClient = android.webkit.WebChromeClient()
                            webViewClient = android.webkit.WebViewClient()
                            val embedHtml = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                <style>
                                  * { box-sizing: border-box; }
                                  body, html { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #000; overflow: hidden; }
                                  iframe { border: none; width: 100%; height: 100%; position: absolute; top: 0; left: 0; }
                                </style>
                                </head>
                                <body>
                                <iframe src="https://www.youtube.com/embed/$yId?autoplay=1&playsinline=1&controls=1&enablejsapi=1&fs=1&rel=0" allow="autoplay; fullscreen; encrypted-media; picture-in-picture" allowfullscreen></iframe>
                                </body>
                                </html>
                            """.trimIndent()
                            loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null)
                        }
                    }
                )
            } else {
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
                            val styling = prefs.isEnabled(com.lnxv.player.core.flags.FeatureFlag.SUBTITLE_STYLING)
                            val bgAlpha = if (styling) (prefs.subtitleBgOpacityPct.coerceIn(0, 100) * 255 / 100) else 0x80
                            val edgeType = if (!styling) CaptionStyleCompat.EDGE_TYPE_OUTLINE else when (prefs.subtitleEdge) {
                                "SHADOW" -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                                "NONE" -> CaptionStyleCompat.EDGE_TYPE_NONE
                                else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                            }
                            sv.setBottomPaddingFraction(if (styling) prefs.subtitleBottomPct.coerceIn(0, 40) / 100f else 0.08f)
                            sv.setStyle(
                                CaptionStyleCompat(
                                    fg,
                                    (bgAlpha shl 24),
                                    android.graphics.Color.TRANSPARENT,
                                    edgeType,
                                    android.graphics.Color.BLACK,
                                    if (!styling) null else when (prefs.subtitleFont) {
                                        "SERIF" -> android.graphics.Typeface.SERIF
                                        "MONO" -> android.graphics.Typeface.MONOSPACE
                                        "MEDIUM" -> android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                                        else -> null
                                    }
                                )
                            )
                        }
                    },
                    modifier = surfaceModifier
                )
            }
        }

        com.lnxv.player.multiscreen.MultiScreenHostHooks(
            player = playerManager.exoPlayer,
            onRestore = { playerViewRef?.let { v -> v.player = null; v.player = playerManager.exoPlayer } }
        )
        if (showMultiScreen) {
            com.lnxv.player.multiscreen.MultiScreenHostDialog(
                player = playerManager.exoPlayer,
                onDismiss = { showMultiScreen = false }
            )
        }

        val audioOnlyActive by viewModel.audioOnly.collectAsState()
        if (audioOnlyActive) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (uiState.currentVideo?.title ?: "") + "\n(audio only)",
                    color = Color(0xFFB0B8C8),
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }

        // Gesture detector wrapper
        PlayerGestureDetector(
            modifier = Modifier.fillMaxSize(),
            isLocked = playbackState.isScreenLocked,
            durationMs = playbackState.durationMs,
            currentPositionProvider = { viewModel.positionMs.value },
            brightnessEnabled = prefs.isBrightnessGestureEnabled,
            volumeEnabled = prefs.isVolumeGestureEnabled,
            seekEnabled = prefs.isSeekGestureEnabled,
            longPressEnabled = prefs.isLongPressSpeedEnabled,
            doubleTapSeekMs = prefs.doubleTapSeekSeconds * 1000L,
            sensitivity = if (prefs.isEnabled(com.lnxv.player.core.flags.FeatureFlag.GESTURE_SENSITIVITY)) prefs.gestureSensitivityPct / 100f else 1f,
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
            GestureHud(state = overlayState)

            // Controls Overlay (hidden while a panel/menu is open, otherwise the dimmed controls show through it)
            val panelOpen = uiState.showSpeedDialog || uiState.showAspectRatioDialog || uiState.showQualityDialog || uiState.showAudioDialog ||
                uiState.showSubtitleDialog || uiState.showPropertiesDialog || isMoreMenuOpen
            val controlsShown = (uiState.isControlsVisible || playbackState.isScreenLocked) && !panelOpen
            // While the controls are up, buffering is shown as a ring on the play button itself (no second circle behind it).
            BufferingIndicator(isBuffering = playbackState.isBuffering && !controlsShown)
            AnimatedVisibility(
                visible = controlsShown,
                enter = fadeIn(tween(180)) + slideInVertically(
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.88f, stiffness = 420f)
                ) { it / 10 },
                exit = fadeOut(tween(140)) + slideOutVertically(
                    animationSpec = tween(160, easing = com.lnxv.player.core.design.MotionTokens.IosStandardAccelerate)
                ) { it / 12 }
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
                            contentDescription = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.cd_unlock_screen),
                            onClick = { viewModel.toggleScreenLock() },
                            tint = NeonCyan,
                            size = 54.dp
                        )
                    }
                } else {
                    IosPlayerControls(
                        viewModel = viewModel,
                        uiState = uiState,
                        playbackState = playbackState,
                        queueUi = queueUi,
                        activity = activity,
                        reduceEffects = reduceEffects,
                        onClose = {
                            viewModel.closePlayer()
                            onBackClick()
                        },
                        onMore = { isMoreMenuOpen = true },
                        onSleepTimer = { showSleepDialog = true },
                        onCycleOrientation = {
                            orientationMode = (orientationMode + 1) % 3
                            activity?.requestedOrientation = when (orientationMode) {
                                1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    )
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
        val anyPanelOpen = uiState.showSpeedDialog || uiState.showAspectRatioDialog || uiState.showQualityDialog || uiState.showAudioDialog ||
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

        PlayerSidePanels(
            viewModel = viewModel,
            uiState = uiState,
            playbackState = playbackState,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            subtitleDelayMs = subtitleDelayMs,
            hasExternalSubtitle = hasExternalSubtitle,
            subtitleEncoding = subtitleEncoding,
            glassBackdrop = glassBackdrop,
            reportScope = reportScope,
            context = context,
            onLoadSubtitleFile = { subtitleFileLauncher.launch(arrayOf("*/*")) }
        )

        // Overflow menu (drawn inside the player, scrollable)
        PlayerOverflowMenu(
            visible = isMoreMenuOpen,
            onDismiss = { isMoreMenuOpen = false },
            backdrop = glassBackdrop
        ) {
            PlayerMoreMenuItems(
                viewModel = viewModel,
                uiState = uiState,
                playbackState = playbackState,
                queueUi = queueUi,
                audioDelayMs = audioDelayMs,
                context = context,
                playerView = playerViewRef,
                videoRotation = videoRotation,
                videoFlip = videoFlip,
                closeMenu = { isMoreMenuOpen = false },
                openSleepTimer = { showSleepDialog = true },
                onRotateVideo = { videoRotation = (videoRotation + 90) % 360 },
                onFlipVideo = { videoFlip = !videoFlip },
                onMultiScreen = { showMultiScreen = true }
            )
        }

    }
}

