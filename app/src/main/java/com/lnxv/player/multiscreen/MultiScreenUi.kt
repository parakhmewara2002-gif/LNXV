package com.lnxv.player.multiscreen

import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.widget.FrameLayout
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import com.lnxv.player.core.design.GlassDialog
import com.lnxv.player.core.design.GlassDialogButton
import com.lnxv.player.core.design.GlassSwitch
import com.lnxv.player.core.design.appColors
import kotlin.math.roundToInt

/** Draws [rect] (dp, may start at negative coordinates) of a video into a clipped area. Used for Mirror and Span. */
@Composable
fun VideoSlice(player: ExoPlayer, rect: DpRect, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val d = LocalDensity.current.density
    val tv = remember { TextureView(ctx) }
    DisposableEffect(player, tv) {
        player.setVideoTextureView(tv)
        onDispose { player.clearVideoTextureView(tv) }
    }
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { c ->
            FrameLayout(c).apply {
                clipChildren = true
                setBackgroundColor(0xFF000000.toInt())
                addView(tv, FrameLayout.LayoutParams(1, 1))
            }
        },
        update = {
            val lp = tv.layoutParams as FrameLayout.LayoutParams
            lp.width = (rect.width * d).roundToInt().coerceAtLeast(1)
            lp.height = (rect.height * d).roundToInt().coerceAtLeast(1)
            tv.layoutParams = lp
            tv.translationX = rect.left * d
            tv.translationY = rect.top * d
        }
    )
}

/**
 * Always composed by the player screen. Reports this phone's size to the session, ends the session when the
 * player screen goes away, and (in Span mode) replaces the normal video view by this phone's slice.
 * [onRestore] re-attaches the normal PlayerView surface afterwards.
 */
@Composable
fun MultiScreenHostHooks(player: ExoPlayer, onRestore: () -> Unit) {
    val host by MultiScreen.hostFlow.collectAsState()
    val h = host ?: return
    val cfg = LocalConfiguration.current
    LaunchedEffect(h, cfg.screenWidthDp, cfg.screenHeightDp) {
        h.setScreenDp(cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat())
    }
    DisposableEffect(h) { onDispose { MultiScreen.stopHost() } }
}

@Composable
internal fun MsChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) appColors.accent.copy(alpha = 0.28f) else appColors.glassSubtle)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) appColors.accent else appColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun MsStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = appColors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        MsChip("−", false, onMinus)
        Text(value, color = appColors.textPrimary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp))
        MsChip("+", false, onPlus)
    }
}

@Composable
fun MultiScreenHostDialog(player: ExoPlayer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val host by MultiScreen.hostFlow.collectAsState()
    val h = host
    val ui = h?.ui?.collectAsState()?.value
    val dismiss: (@Composable () -> Unit)? = if (h != null) {
        { GlassDialogButton("Stop sharing", { MultiScreen.stopHost() }, destructive = true) }
    } else null
    GlassDialog(
        onDismissRequest = onDismiss,
        confirmButton = { GlassDialogButton("Close", onDismiss, primary = true) },
        dismissButton = dismiss,
        title = { Text("Second screen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (h == null || ui == null) {
                    Text(
                        "Play this movie on another phone over Wi-Fi. Both phones need LNXV and must be on the same Wi-Fi " +
                            "(or one phone's hotspot). Bluetooth cannot carry video, so it is not used.",
                        color = appColors.textSecondary, fontSize = 13.sp
                    )
                    GlassDialogButton("Start sharing", { MultiScreen.startHost(context, player) }, primary = true)
                } else {
                    val addr = if (ui.ip != null) "${ui.ip}:${ui.port}" else "no Wi-Fi address found"
                    Text(
                        "On the other phone open LNXV → Home → Join screen.\nAddress: $addr\nPIN: ${ui.pin}",
                        color = appColors.textPrimary, fontSize = 14.sp
                    )
                    Text(
                        if (ui.connected) "Connected: ${ui.clientName}" else "Waiting for the other phone…",
                        color = if (ui.connected) appColors.accent else appColors.textSecondary, fontSize = 13.sp
                    )
                    Text("Mode", color = appColors.textSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MsChip("Mirror", ui.mode == MsMode.MIRROR, { h.setMode(MsMode.MIRROR) })
                        MsChip("Speaker", ui.mode == MsMode.SPEAKER, { h.setMode(MsMode.SPEAKER) })
                    }
                    Text(
                        when (ui.mode) {
                            MsMode.MIRROR -> "Both phones show the video and play the sound in sync."
                            MsMode.SPEAKER -> "The other phone plays the audio as a remote speaker."
                        },
                        color = appColors.textSecondary, fontSize = 12.sp
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mute this phone", color = appColors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        GlassSwitch(checked = ui.hostMuted, onCheckedChange = { h.setHostMuted(it) })
                    }
                    ui.note?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp) }
                    Spacer(Modifier.height(2.dp).width(1.dp))
                }
            }
        }
    )
}
