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

/**
 * Gesture HUD (brightness / volume / seek / zoom): a glass pill that springs in near the top of the video,
 * with a progress bar for brightness/volume. Keeps the last gesture's content while it fades out.
 */
@Composable
fun GestureHud(state: GestureOverlayState) {
    val reduce = com.lnxv.player.core.design.LocalReduceEffects.current
    val active = state.activeGesture != GestureType.NONE
    // Non-state holder: remembers the last active gesture so the pill does not change content during its exit fade.
    val last = remember { arrayOfNulls<GestureOverlayState>(1) }
    if (active) last[0] = state
    val shown = last[0] ?: state
    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(100)) + (
            if (reduce) androidx.compose.animation.EnterTransition.None
            else androidx.compose.animation.scaleIn(
                initialScale = 0.92f,
                animationSpec = com.lnxv.player.core.design.MotionTokens.SpringSnappy
            )
        ),
        exit = fadeOut(tween(140)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(top = 140.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val (label, value, fraction) = when (shown.activeGesture) {
                GestureType.BRIGHTNESS -> Triple("Brightness", "${shown.brightnessPercent}%", shown.brightnessPercent / 100f)
                GestureType.VOLUME -> Triple("Volume", "${shown.volumePercent}%", shown.volumePercent / 100f)
                GestureType.SEEK -> {
                    val d = shown.seekDeltaMs
                    Triple("${if (d >= 0) "+" else "-"}${abs(d) / 1000}s", VideoItem.formatDuration(shown.seekTargetMs), null)
                }
                GestureType.ZOOM -> Triple("Zoom", "${"%.1f".format(shown.zoomScale)}x", null)
                else -> Triple("", "", null)
            }
            Column(
                modifier = Modifier
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = Color(0x99101622),
                        borderColor = Color(0x40FFFFFF)
                    )
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = label, color = Color(0xB3FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                if (fraction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                .background(NeonCyan)
                        )
                    }
                }
            }
        }
    }
}

/** Buffering state: glass disc that fades in/out instead of popping. */
@Composable
fun BufferingIndicator(isBuffering: Boolean) {
    AnimatedVisibility(
        visible = isBuffering,
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
}
