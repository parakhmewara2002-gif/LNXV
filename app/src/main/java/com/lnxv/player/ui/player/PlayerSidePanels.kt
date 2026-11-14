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
import androidx.compose.foundation.layout.BoxScope
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

/** Side panels (speed / aspect / audio / subtitles / properties). Extracted from PlayerScreen (Phase 4 split). */
@Composable
fun BoxScope.PlayerSidePanels(
    viewModel: PlayerViewModel,
    uiState: PlayerUiState,
    playbackState: com.lnxv.player.core.model.PlaybackState,
    audioTracks: List<com.lnxv.player.core.model.AudioTrackInfo>,
    subtitleTracks: List<com.lnxv.player.core.model.SubtitleTrackInfo>,
    subtitleDelayMs: Long,
    hasExternalSubtitle: Boolean,
    glassBackdrop: ImageBitmap?,
    reportScope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    onLoadSubtitleFile: () -> Unit
) {
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
            onLoadFile = { onLoadSubtitleFile() },
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
}
