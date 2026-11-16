package com.lnxv.player.ui.settings

import com.lnxv.player.core.design.GlassProgress
import com.lnxv.player.core.design.GlassSwitch
import com.lnxv.player.core.design.GlassDialog
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.haze
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnxv.player.core.design.ambientBackground
import com.lnxv.player.core.design.appColors
import com.lnxv.player.core.design.GlassIconButton
import com.lnxv.player.core.design.GlassPanel
import com.lnxv.player.core.design.NeonCyan
import com.lnxv.player.core.design.Typography
import com.lnxv.player.core.update.UpdateState

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val prefs by viewModel.userPreferences.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    val updaterEnabled = androidx.compose.ui.res.booleanResource(com.lnxv.player.R.bool.updater_enabled)
    LaunchedEffect(Unit) { if (updaterEnabled) viewModel.checkForUpdate() }

    val hazeState = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }
    val reduceEffects = com.lnxv.player.core.design.LocalReduceEffects.current
    val queryState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val query = queryState.value.trim()
    fun show(vararg keys: String) = query.isEmpty() || keys.any { it.contains(query, ignoreCase = true) }
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    var confirmResetFlags by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (confirmResetFlags) {
        GlassDialog(
            onDismissRequest = { confirmResetFlags = false },
            title = { Text("Reset experimental flags?") },
            text = { Text("Every flag under Advanced returns to its default. Your PIN, vault and other settings are not touched.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.resetFlags(); confirmResetFlags = false }) { Text("Reset") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmResetFlags = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().ambientBackground(),
        containerColor = Color.Transparent,
        topBar = {
            com.lnxv.player.core.design.GlassTopBar(
                title = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_title),
                onBack = onBackClick,
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (reduceEffects) Modifier else Modifier.haze(state = hazeState)),
            // The list scrolls under the glass top bar (so there is something to blur); padding keeps the first row clear of it.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + 12.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                com.lnxv.player.core.design.GlassSearchField(
                    value = queryState.value,
                    onValueChange = { queryState.value = it },
                    placeholder = "Search settings"
                )
            }

            if (show("Appearance & Glass Engine", "appearance","theme","glass","amoled","dark","light")) item {
                SectionHeader("Appearance & Glass Engine")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_theme),
                            color = appColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (mode, label) ->
                                val selected = prefs.themeMode == mode
                                val chipBg by animateColorAsState(
                                    targetValue = if (selected) appColors.accent.copy(alpha = 0.22f) else appColors.glassSubtle,
                                    animationSpec = tween(200),
                                    label = "theme_chip_bg"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(chipBg)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) appColors.accent else appColors.glassBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.setThemeMode(mode) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) appColors.accent else appColors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        SettingSwitchRow(
                            title = "AMOLED True Black",
                            subtitle = "Pure black background in Dark theme (saves battery on OLED screens)",
                            isChecked = prefs.isAmoled,
                            onCheckedChange = { viewModel.toggleAmoled(it) }
                        )
                    }
                }
            }

            if (show("Playback & Gestures", "playback","gesture","subtitle","speed","resume","seek")) item {
                SectionHeader("Playback & Gestures")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingSwitchRow(
                            title = "Background Audio Playback",
                            subtitle = "Continue playing audio when app is minimized",
                            isChecked = prefs.isBackgroundPlayEnabled,
                            onCheckedChange = { viewModel.toggleBackgroundPlay(it) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SettingSwitchRow(
                            title = "Auto Picture-in-Picture",
                            subtitle = "Switch to floating mini player upon pressing Home",
                            isChecked = prefs.isAutoPiPEnabled,
                            onCheckedChange = { viewModel.toggleAutoPiP(it) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SettingSwitchRow(
                            title = "Brightness gesture",
                            subtitle = "Swipe vertically on the left side",
                            isChecked = prefs.isBrightnessGestureEnabled,
                            onCheckedChange = { viewModel.toggleBrightnessGesture(it) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SettingSwitchRow(
                            title = "Volume gesture",
                            subtitle = "Swipe vertically on the right side",
                            isChecked = prefs.isVolumeGestureEnabled,
                            onCheckedChange = { viewModel.toggleVolumeGesture(it) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SettingSwitchRow(
                            title = "Seek gesture",
                            subtitle = "Swipe horizontally to seek",
                            isChecked = prefs.isSeekGestureEnabled,
                            onCheckedChange = { viewModel.toggleSeekGesture(it) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SettingSwitchRow(
                            title = "Long-press speed",
                            subtitle = "Hold to play at 2x",
                            isChecked = prefs.isLongPressSpeedEnabled,
                            onCheckedChange = { viewModel.toggleLongPressGesture(it) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SettingSwitchRow(
                            title = "Remember Playback Speed",
                            subtitle = "Use the last speed you picked for every video",
                            isChecked = prefs.rememberSpeed,
                            onCheckedChange = { viewModel.setRememberSpeed(it) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = when (prefs.resumePlaybackMode) {
                                        "ALWAYS" -> "ASK"
                                        "ASK" -> "NEVER"
                                        else -> "ALWAYS"
                                    }
                                    viewModel.setResumeMode(next)
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_resume), color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_tap_change), color = appColors.textSecondary, fontSize = 12.sp)
                            }
                            Text(
                                text = when (prefs.resumePlaybackMode) {
                                    "ASK" -> "Ask me"
                                    "NEVER" -> "Start over"
                                    else -> "Always resume"
                                },
                                color = appColors.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_sub_size), color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(text = "${prefs.subtitleTextSize.toInt()} sp", color = appColors.textSecondary, fontSize = 12.sp)
                            }
                            Text(
                                text = "  −  ",
                                color = appColors.accent,
                                fontSize = 22.sp,
                                modifier = Modifier.clickable { viewModel.setSubtitleSize(prefs.subtitleTextSize - 2f) }
                            )
                            Text(
                                text = "  +  ",
                                color = appColors.accent,
                                fontSize = 22.sp,
                                modifier = Modifier.clickable { viewModel.setSubtitleSize(prefs.subtitleTextSize + 2f) }
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        StepRow("Thumbnail size", "${prefs.thumbSizeDp}dp", { viewModel.setThumbSize(prefs.thumbSizeDp - 20) }, { viewModel.setThumbSize(prefs.thumbSizeDp + 20) })
                        if (prefs.isEnabled(com.lnxv.player.core.flags.FeatureFlag.GESTURE_SENSITIVITY)) {
                            Spacer(modifier = Modifier.height(14.dp))
                            StepRow("Gesture sensitivity", "${prefs.gestureSensitivityPct}%", { viewModel.setGestureSensitivity(prefs.gestureSensitivityPct - 10) }, { viewModel.setGestureSensitivity(prefs.gestureSensitivityPct + 10) })
                        }
                        if (prefs.isEnabled(com.lnxv.player.core.flags.FeatureFlag.SUBTITLE_STYLING)) {
                            Spacer(modifier = Modifier.height(14.dp))
                            StepRow("Subtitle background", "${prefs.subtitleBgOpacityPct}%", { viewModel.setSubtitleBg(prefs.subtitleBgOpacityPct - 10) }, { viewModel.setSubtitleBg(prefs.subtitleBgOpacityPct + 10) })
                            Spacer(modifier = Modifier.height(14.dp))
                            StepRow("Subtitle position (from bottom)", "${prefs.subtitleBottomPct}%", { viewModel.setSubtitleBottom(prefs.subtitleBottomPct - 2) }, { viewModel.setSubtitleBottom(prefs.subtitleBottomPct + 2) })
                            Spacer(modifier = Modifier.height(14.dp))
                            val fonts = listOf("DEFAULT", "SERIF", "MONO", "MEDIUM")
                            StepRow("Subtitle font", prefs.subtitleFont.lowercase(), { viewModel.setSubtitleFont(fonts[(fonts.indexOf(prefs.subtitleFont).coerceAtLeast(0) + fonts.size - 1) % fonts.size]) }, { viewModel.setSubtitleFont(fonts[(fonts.indexOf(prefs.subtitleFont).coerceAtLeast(0) + 1) % fonts.size]) })
                            Spacer(modifier = Modifier.height(14.dp))
                            val edges = listOf("OUTLINE", "SHADOW", "NONE")
                            StepRow("Subtitle edge", prefs.subtitleEdge.lowercase(), { viewModel.setSubtitleEdge(edges[(edges.indexOf(prefs.subtitleEdge).coerceAtLeast(0) + edges.size - 1) % edges.size]) }, { viewModel.setSubtitleEdge(edges[(edges.indexOf(prefs.subtitleEdge).coerceAtLeast(0) + 1) % edges.size]) })
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_sub_color), color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(
                                    "#FFFFFFFF" to Color(0xFFFFFFFF),
                                    "#FFFFEB3B" to Color(0xFFFFEB3B),
                                    "#FF00E5FF" to Color(0xFF00E5FF),
                                    "#FF69F0AE" to Color(0xFF69F0AE)
                                ).forEach { (hex, color) ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(color, CircleShape)
                                            .border(
                                                width = if (prefs.subtitleTextColorHex.equals(hex, ignoreCase = true)) 3.dp else 1.dp,
                                                color = if (prefs.subtitleTextColorHex.equals(hex, ignoreCase = true)) appColors.accent else appColors.textTertiary,
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.setSubtitleColor(hex) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (updaterEnabled) if (show("Updates", "update","version")) item {
                SectionHeader("Updates")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    val st = updateState
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = appColors.accent
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Version ${viewModel.currentVersion}",
                                    color = appColors.textPrimary,
                                    style = Typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (st) {
                                        is UpdateState.Idle -> "Tap below to check for updates"
                                        is UpdateState.Checking -> "Checking for updates..."
                                        is UpdateState.UpToDate -> "You're on the latest version"
                                        is UpdateState.Available -> "Update available: v${st.info.versionName}"
                                        is UpdateState.Downloading -> "Downloading ${(st.progress * 100).toInt()}%"
                                        is UpdateState.Error -> st.message
                                    },
                                    color = appColors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (st is UpdateState.Downloading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            GlassProgress(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
                        }
                        if (st is UpdateState.Available && st.info.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = st.info.notes.trim().take(600),
                                color = appColors.textTertiary,
                                fontSize = 12.sp,
                                maxLines = 8
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingSwitchRow(
                            title = "Download updates on Wi-Fi only",
                            subtitle = "Avoid using mobile data for updates",
                            isChecked = prefs.updateWifiOnly,
                            onCheckedChange = { viewModel.setUpdateWifiOnly(it) }
                        )
                        if (st is UpdateState.Available) {
                            androidx.compose.material3.TextButton(onClick = { viewModel.skipThisVersion(st.info) }) {
                                Text("Skip this version", color = appColors.textTertiary)
                            }
                        }
                        if (st !is UpdateState.Checking && st !is UpdateState.Downloading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(appColors.accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (st is UpdateState.Available) viewModel.downloadAndInstall(st.info)
                                        else viewModel.checkForUpdate()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (st is UpdateState.Available) "Download & install" else "Check for updates",
                                    color = appColors.accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            if (show("Appearance & performance", "performance","effects","blur","material","reduce")) item {
                SectionHeader("Appearance & performance")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            SettingSwitchRow(
                                title = "Material You colors",
                                subtitle = "Use your wallpaper colors (Android 12+)",
                                isChecked = prefs.dynamicColor,
                                onCheckedChange = { viewModel.setDynamicColor(it) }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.cycleEffectsMode(prefs.effectsMode) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Reduce effects", color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Turns off backdrop blur and heavy animations. Auto enables it on low-RAM / 60 Hz devices.",
                                    color = appColors.textTertiary, fontSize = 12.sp
                                )
                            }
                            Text(
                                text = when (prefs.effectsMode) { "ON" -> "On"; "OFF" -> "Off"; else -> "Auto" },
                                color = appColors.accent, fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (show("Security", "security","vault","pin","biometric","screenshot","lock")) item {
                SectionHeader("Security")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingSwitchRow(
                            title = "Block screenshots everywhere",
                            subtitle = "Vault screens always block screenshots; this extends it to the whole app",
                            isChecked = prefs.blockScreenshotsEverywhere,
                            onCheckedChange = { viewModel.setBlockScreenshots(it) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.cycleAutoLock(prefs.vaultAutoLockMs) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Vault auto-lock", color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text("Tap to change. Always locks when the screen turns off.", color = appColors.textTertiary, fontSize = 12.sp)
                            }
                            Text(
                                text = when (prefs.vaultAutoLockMs) {
                                    0L -> "Immediately"
                                    30_000L -> "30 seconds"
                                    60_000L -> "1 minute"
                                    else -> "5 minutes"
                                },
                                color = appColors.accent, fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (show("Advanced > Experimental", "advanced","experimental","flag","developer")) item {
                SectionHeader("Advanced > Experimental")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        com.lnxv.player.core.flags.FeatureFlag.values().forEachIndexed { index, flag ->
                            if (index > 0) Spacer(modifier = Modifier.height(14.dp))
                            SettingSwitchRow(
                                title = flag.title,
                                subtitle = flag.description,
                                isChecked = prefs.isEnabled(flag),
                                onCheckedChange = { viewModel.setFlag(flag, it) }
                            )
                        }
                    }
                }
            }

            if (show("Storage", "storage","cache","clear")) item {
                SectionHeader("Storage")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    var cacheBytes by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(-1L) }
                    var dataBytes by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(-1L) }
                    val refresh: () -> Unit = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val c = com.lnxv.player.core.util.StorageUtil.sizeOf(ctx.cacheDir)
                            val d = com.lnxv.player.core.util.StorageUtil.sizeOf(ctx.filesDir)
                            cacheBytes = c
                            dataBytes = d
                        }
                    }
                    LaunchedEffect(Unit) { refresh() }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cache: ${if (cacheBytes < 0) "…" else com.lnxv.player.core.util.StorageUtil.format(cacheBytes)}", color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("App data (vault, database): ${if (dataBytes < 0) "…" else com.lnxv.player.core.util.StorageUtil.format(dataBytes)}", color = appColors.textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Clear thumbnails",
                            color = appColors.accent,
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val loader = coil.Coil.imageLoader(ctx)
                                    loader.memoryCache?.clear()
                                    loader.diskCache?.clear()
                                    refresh()
                                }
                            }.padding(vertical = 10.dp)
                        )
                        Text(
                            "Clear cache (thumbnails, downloaded updates, temp subtitles)",
                            color = appColors.accent,
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    coil.Coil.imageLoader(ctx).memoryCache?.clear()
                                    com.lnxv.player.core.util.StorageUtil.clearContents(ctx.cacheDir)
                                    refresh()
                                }
                            }.padding(vertical = 10.dp)
                        )
                        Text("Your videos and the encrypted vault are never touched by these actions.", color = appColors.textTertiary, fontSize = 11.sp)
                    }
                }
            }

            if (show("Privacy", "privacy","permission")) item {
                SectionHeader("Privacy")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val bioCtx = androidx.compose.ui.platform.LocalContext.current
                        if (prefs.isEnabled(com.lnxv.player.core.flags.FeatureFlag.BIOMETRIC_UNLOCK) && com.lnxv.player.core.security.BiometricGate.canAuthenticate(bioCtx)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Unlock vault with biometrics", color = appColors.textPrimary, fontSize = 14.sp)
                                GlassSwitch(
                                    checked = prefs.vaultBiometric,
                                    onCheckedChange = { want ->
                                        if (!want) viewModel.setVaultBiometric(false)
                                        else (bioCtx as? androidx.fragment.app.FragmentActivity)?.let { a ->
                                            // Enabling needs a successful biometric confirmation first.
                                            com.lnxv.player.core.security.BiometricGate.prompt(a, "Enable biometric unlock", "Cancel", { viewModel.setVaultBiometric(true) })
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Text("No analytics. No tracking. No ads.", color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Stored only on this device: watch history and resume positions, favorites, playlists, settings, recent network URLs, and the encrypted vault. " +
                                "The only network requests are the update check to GitHub (sideload build) and streams you open yourself.",
                            color = appColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (show("About LNXV", "about","version","info")) item {
                SectionHeader("About LNXV")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = appColors.accent
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "LNXV Video Player v${viewModel.currentVersion}",
                                color = appColors.textPrimary,
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "AndroidX Media3 ExoPlayer • Jetpack Compose • Clean Architecture",
                                color = appColors.textTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (show("Developer", "system info", "device", "build", "debug", "reset flags")) item {
                SectionHeader("Developer")
                com.lnxv.player.core.design.GlassGroup {
                    com.lnxv.player.core.design.GlassInfoRow("App version", viewModel.currentVersion)
                    com.lnxv.player.core.design.GlassDivider()
                    com.lnxv.player.core.design.GlassInfoRow("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    com.lnxv.player.core.design.GlassDivider()
                    com.lnxv.player.core.design.GlassInfoRow("Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    com.lnxv.player.core.design.GlassDivider()
                    com.lnxv.player.core.design.GlassInfoRow("ABI", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
                    com.lnxv.player.core.design.GlassDivider()
                    com.lnxv.player.core.design.GlassInfoRow("Effects", if (reduceEffects) "Reduced" else "Full (backdrop blur on)")
                    com.lnxv.player.core.design.GlassDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        androidx.compose.material3.TextButton(onClick = {
                            val info = "LNXV ${viewModel.currentVersion} | Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) | " +
                                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} | ${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "?"} | effects=${if (reduceEffects) "reduced" else "full"}"
                            val cm = settingsContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("LNXV system info", info))
                        }) { Text("Copy system info", color = appColors.accent) }
                        androidx.compose.material3.TextButton(onClick = { confirmResetFlags = true }) {
                            Text("Reset flags", color = appColors.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = value, color = appColors.textSecondary, fontSize = 12.sp)
        }
        Text(text = "  −  ", color = appColors.accent, fontSize = 22.sp, modifier = Modifier.clickable(onClick = onMinus))
        Text(text = "  +  ", color = appColors.accent, fontSize = 22.sp, modifier = Modifier.clickable(onClick = onPlus))
    }
}

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title.uppercase(),
            color = appColors.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.35.sp
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(appColors.glassBorder, Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = appColors.textTertiary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        GlassSwitch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}
