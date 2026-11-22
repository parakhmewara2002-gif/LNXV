package com.lnxv.player.multiscreen

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.text.KeyboardOptions
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.ui.AspectRatioFrameLayout
import com.lnxv.player.core.design.GlassDialogButton
import com.lnxv.player.core.design.GlassTopBar
import com.lnxv.player.core.design.ambientBackground
import com.lnxv.player.core.design.appColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextFieldDefaults

/** "Join screen": this phone becomes the speaker / second screen of another LNXV phone. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MultiScreenReceiverScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rx = remember { MsReceiver(ctx.applicationContext) }
    val ui by rx.ui.collectAsState()
    val cfg = LocalConfiguration.current
    var address by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showControls by remember { mutableStateOf(true) }
    val activity = ctx as? Activity
    val connected = ui.status == RxStatus.CONNECTED
    val showsVideo = connected && ui.hasMedia && ui.mode != MsMode.SPEAKER

    DisposableEffect(Unit) {
        rx.startDiscovery()
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            rx.release()
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(showsVideo) {
        val act = activity
        val prev = act?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (showsVideo && act != null) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            try {
                val c = WindowCompat.getInsetsController(act.window, act.window.decorView)
                c.hide(WindowInsetsCompat.Type.systemBars())
                c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } catch (_: Exception) {}
        }
        onDispose {
            if (showsVideo && act != null) {
                act.requestedOrientation = prev
                try { WindowCompat.getInsetsController(act.window, act.window.decorView).show(WindowInsetsCompat.Type.systemBars()) } catch (_: Exception) {}
            }
        }
    }
    BackHandler { onBack() }

    if (showsVideo) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        player = rx.player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { pv ->
                    if (pv.player !== rx.player) {
                        pv.player = rx.player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { showControls = !showControls })
            if (showControls) ReceiverControls(ui, rx, onBack, Modifier.align(Alignment.BottomCenter))
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize().ambientBackground(),
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = "Join screen",
                    onBack = onBack,
                    hazeState = null
                )
            }
        ) { paddingValues ->
            if (connected) {
                Column(
                    Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (ui.mode == MsMode.SPEAKER) "Speaker mode" else "Connected", color = appColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (ui.title.isNotEmpty()) ui.title else "Waiting for the movie…", color = appColors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                    ReceiverControls(ui, rx, onBack, Modifier)
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "On the other phone play a movie, open ⋮ → Second screen and tap Start sharing. Both phones must be on the same Wi-Fi.",
                        color = appColors.textSecondary, fontSize = 14.sp
                    )
                    if (ui.found.isNotEmpty()) {
                        Text("Found nearby", color = appColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        ui.found.forEach { f ->
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(appColors.glassFill)
                                    .clickable { address = "${f.ip}:${f.port}" }.padding(14.dp)
                            ) { Text("${f.name}  (${f.ip}:${f.port})", color = appColors.textPrimary, fontSize = 14.sp) }
                        }
                    }
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        label = { Text("Address (shown on the other phone)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary,
                            focusedBorderColor = appColors.accent,
                            unfocusedBorderColor = appColors.glassBorder,
                            focusedLabelColor = appColors.accent,
                            unfocusedLabelColor = appColors.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4) },
                        singleLine = true,
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary,
                            focusedBorderColor = appColors.accent,
                            unfocusedBorderColor = appColors.glassBorder,
                            focusedLabelColor = appColors.accent,
                            unfocusedLabelColor = appColors.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    ui.message?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassDialogButton(
                            if (ui.status == RxStatus.CONNECTING) "Connecting…" else "Connect",
                            {
                                val ip = address.substringBefore(':').trim()
                                val port = address.substringAfter(':', "").trim().toIntOrNull()
                                if (ip.isEmpty() || port == null) {
                                    android.widget.Toast.makeText(ctx, "Enter the address like 192.168.1.5:40000", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    rx.connect(ip, port, pin, cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat())
                                }
                            },
                            enabled = ui.status != RxStatus.CONNECTING && pin.length == 4, primary = true
                        )
                        GlassDialogButton("Back", onBack)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiverControls(ui: RxUi, rx: MsReceiver, onBack: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(16.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xAA1C1F26)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MsStepper(
            "Play earlier by", "${ui.delayMs} ms",
            { rx.setDelayMs(ui.delayMs - 20) }, { rx.setDelayMs(ui.delayMs + 20) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassDialogButton("Disconnect", { rx.disconnect(); onBack() }, destructive = true)
        }
    }
}
