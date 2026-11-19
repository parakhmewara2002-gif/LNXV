package com.lnxv.player.ui.settings

import com.lnxv.player.core.design.GlassProgress
import com.lnxv.player.core.design.GlassSwitch
import com.lnxv.player.core.design.GlassDialog
import com.lnxv.player.core.design.GlassDialogButton
import com.lnxv.player.core.design.GlassButton
import com.lnxv.player.core.design.GlassDivider
import com.lnxv.player.core.design.GlassSegmented
import com.lnxv.player.core.design.GlassStepper
import com.lnxv.player.core.design.DestructiveRed
import com.lnxv.player.core.design.bouncyClickable
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
    onBackClick: () -> Unit,
    onOpenLicenses: () -> Unit = {}
) {
    val prefs by viewModel.userPreferences.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    val updaterEnabled = androidx.compose.ui.res.booleanResource(com.lnxv.player.R.bool.updater_enabled)
    val onlineSubsEnabled = androidx.compose.ui.res.booleanResource(com.lnxv.player.R.bool.online_subtitles_enabled)
    LaunchedEffect(Unit) { if (updaterEnabled) viewModel.checkForUpdate() }

    val hazeState = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }
    val reduceEffects = com.lnxv.player.core.design.LocalReduceEffects.current
    val queryState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val query = queryState.value.trim()
    fun show(section: com.lnxv.player.core.model.SettingsSection) = com.lnxv.player.core.model.SettingsSearch.matches(query, section)
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    var confirmResetFlags by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (confirmResetFlags) {
        GlassDialog(
            onDismissRequest = { confirmResetFlags = false },
            title = { Text("Reset experimental flags?") },
            text = { Text("Every flag under Advanced returns to its default. Your PIN, vault and other settings are not touched.") },
            confirmButton = {
                GlassDialogButton(text = "Reset", primary = true, destructive = true, onClick = { viewModel.resetFlags(); confirmResetFlags = false })
            },
            dismissButton = {
                GlassDialogButton(text = "Cancel", onClick = { confirmResetFlags = false })
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

            if (show(com.lnxv.player.core.model.SettingsSection.APPEARANCE_GLASS)) item {
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
                        val themeModes = listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark")
                        GlassSegmented(
                            options = themeModes.map { it.second },
                            selectedIndex = themeModes.indexOfFirst { it.first == prefs.themeMode }.coerceAtLeast(0),
                            onSelect = { viewModel.setThemeMode(themeModes[it].first) }
                        )
                        GlassDivider()
                        SettingSwitchRow(
                            title = "AMOLED True Black",
                            subtitle = "Pure black background in Dark theme (saves battery on OLED screens)",
                            isChecked = prefs.isAmoled,
                            onCheckedChange = { viewModel.toggleAmoled(it) }
                        )
                    }
                }
            }

            if (show(com.lnxv.player.core.model.SettingsSection.PLAYBACK)) item {
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
                        GlassDivider()
                        SettingSwitchRow(
                            title = "Auto Picture-in-Picture",
                            subtitle = "Switch to floating mini player upon pressing Home",
                            isChecked = prefs.isAutoPiPEnabled,
                            onCheckedChange = { viewModel.toggleAutoPiP(it) }
                        )
                        GlassDivider()
                        SettingSwitchRow(
                            title = "Brightness gesture",
                            subtitle = "Swipe vertically on the left side",
                            isChecked = prefs.isBrightnessGestureEnabled,
                            onCheckedChange = { viewModel.toggleBrightnessGesture(it) }
                        )
                        GlassDivider()
                        SettingSwitchRow(
                            title = "Volume gesture",
                            subtitle = "Swipe vertically on the right side",
                            isChecked = prefs.isVolumeGestureEnabled,
                            onCheckedChange = { viewModel.toggleVolumeGesture(it) }
                        )
                        GlassDivider()
                        SettingSwitchRow(
                            title = "Seek gesture",
                            subtitle = "Swipe horizontally to seek",
                            isChecked = prefs.isSeekGestureEnabled,
                            onCheckedChange = { viewModel.toggleSeekGesture(it) }
                        )
                        GlassDivider()
                        SettingSwitchRow(
                            title = "Long-press speed",
                            subtitle = "Hold to play at 2x",
                            isChecked = prefs.isLongPressSpeedEnabled,
                            onCheckedChange = { viewModel.toggleLongPressGesture(it) }
                        )
                        GlassDivider()
                        SettingSwitchRow(
                            title = "Remember Playback Speed",
                            subtitle = "Use the last speed you picked for every video",
                            isChecked = prefs.rememberSpeed,
                            onCheckedChange = { viewModel.setRememberSpeed(it) }
                        )
                        GlassDivider()
                        SettingValueRow(
                            title = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_resume),
                            subtitle = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_tap_change),
                            value = when (prefs.resumePlaybackMode) {
                                "ASK" -> "Ask me"
                                "NEVER" -> "Start over"
                                else -> "Always resume"
                            },
                            onClick = {
                                val next = when (prefs.resumePlaybackMode) {
                                    "ALWAYS" -> "ASK"
                                    "ASK" -> "NEVER"
                                    else -> "ALWAYS"
                                }
                                viewModel.setResumeMode(next)
                            }
                        )
                        GlassDivider()
                        val decoderMode = com.lnxv.player.core.model.DecoderMode.fromName(prefs.decoderMode)
                        SettingValueRow(
                            title = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_decoder_title),
                            subtitle = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_decoder_summary),
                            value = androidx.compose.ui.res.stringResource(
                                when (decoderMode) {
                                    com.lnxv.player.core.model.DecoderMode.AUTO -> com.lnxv.player.R.string.settings_decoder_auto
                                    com.lnxv.player.core.model.DecoderMode.HARDWARE -> com.lnxv.player.R.string.settings_decoder_hw
                                    com.lnxv.player.core.model.DecoderMode.SOFTWARE -> com.lnxv.player.R.string.settings_decoder_sw
                                }
                            ),
                            onClick = {
                                val next = when (decoderMode) {
                                    com.lnxv.player.core.model.DecoderMode.AUTO -> com.lnxv.player.core.model.DecoderMode.HARDWARE
                                    com.lnxv.player.core.model.DecoderMode.HARDWARE -> com.lnxv.player.core.model.DecoderMode.SOFTWARE
                                    com.lnxv.player.core.model.DecoderMode.SOFTWARE -> com.lnxv.player.core.model.DecoderMode.AUTO
                                }
                                viewModel.setDecoderMode(next.name)
                            }
                        )
                        GlassDivider()
                        SettingSwitchRow(
                            title = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_exact_seek),
                            subtitle = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_exact_seek_summary),
                            isChecked = prefs.exactSeek,
                            onCheckedChange = { viewModel.setExactSeek(it) }
                        )
                        GlassDivider()
                        StepRow(
                            androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_sub_size),
                            "${prefs.subtitleTextSize.toInt()} sp",
                            { viewModel.setSubtitleSize(prefs.subtitleTextSize - 2f) },
                            { viewModel.setSubtitleSize(prefs.subtitleTextSize + 2f) }
                        )
                        GlassDivider()
                        StepRow("Thumbnail size", "${prefs.thumbSizeDp}dp", { viewModel.setThumbSize(prefs.thumbSizeDp - 20) }, { viewModel.setThumbSize(prefs.thumbSizeDp + 20) })
                        if (prefs.isEnabled(com.lnxv.player.core.flags.FeatureFlag.GESTURE_SENSITIVITY)) {
                            GlassDivider()
                            StepRow("Gesture sensitivity", "${prefs.gestureSensitivityPct}%", { viewModel.setGestureSensitivity(prefs.gestureSensitivityPct - 10) }, { viewModel.setGestureSensitivity(prefs.gestureSensitivityPct + 10) })
                        }
                        if (prefs.isEnabled(com.lnxv.player.core.flags.FeatureFlag.SUBTITLE_STYLING)) {
                            GlassDivider()
                            StepRow("Subtitle background", "${prefs.subtitleBgOpacityPct}%", { viewModel.setSubtitleBg(prefs.subtitleBgOpacityPct - 10) }, { viewModel.setSubtitleBg(prefs.subtitleBgOpacityPct + 10) })
                            GlassDivider()
                            StepRow("Subtitle position (from bottom)", "${prefs.subtitleBottomPct}%", { viewModel.setSubtitleBottom(prefs.subtitleBottomPct - 2) }, { viewModel.setSubtitleBottom(prefs.subtitleBottomPct + 2) })
                            GlassDivider()
                            val fonts = listOf("DEFAULT", "SERIF", "MONO", "MEDIUM")
                            StepRow("Subtitle font", prefs.subtitleFont.lowercase(), { viewModel.setSubtitleFont(fonts[(fonts.indexOf(prefs.subtitleFont).coerceAtLeast(0) + fonts.size - 1) % fonts.size]) }, { viewModel.setSubtitleFont(fonts[(fonts.indexOf(prefs.subtitleFont).coerceAtLeast(0) + 1) % fonts.size]) })
                            GlassDivider()
                            val edges = listOf("OUTLINE", "SHADOW", "NONE")
                            StepRow("Subtitle edge", prefs.subtitleEdge.lowercase(), { viewModel.setSubtitleEdge(edges[(edges.indexOf(prefs.subtitleEdge).coerceAtLeast(0) + edges.size - 1) % edges.size]) }, { viewModel.setSubtitleEdge(edges[(edges.indexOf(prefs.subtitleEdge).coerceAtLeast(0) + 1) % edges.size]) })
                        }
                        GlassDivider()
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

            if (updaterEnabled) if (show(com.lnxv.player.core.model.SettingsSection.UPDATES)) item {
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
                            GlassDialogButton(
                                text = "Skip this version",
                                onClick = { viewModel.skipThisVersion(st.info) },
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                        if (st !is UpdateState.Checking && st !is UpdateState.Downloading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            GlassButton(
                                text = if (st is UpdateState.Available) "Download & install" else "Check for updates",
                                onClick = {
                                    if (st is UpdateState.Available) viewModel.downloadAndInstall(st.info)
                                    else viewModel.checkForUpdate()
                                },
                                isPrimary = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (show(com.lnxv.player.core.model.SettingsSection.PERFORMANCE)) item {
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
                            GlassDivider()
                        }
                        SettingValueRow(
                            title = "Reduce effects",
                            subtitle = "Turns off backdrop blur and heavy animations. Auto enables it on low-RAM / 60 Hz devices.",
                            value = when (prefs.effectsMode) { "ON" -> "On"; "OFF" -> "Off"; else -> "Auto" },
                            onClick = { viewModel.cycleEffectsMode(prefs.effectsMode) }
                        )
                    }
                }
            }

            if (show(com.lnxv.player.core.model.SettingsSection.SECURITY)) item {
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
                        GlassDivider()
                        SettingValueRow(
                            title = "Vault auto-lock",
                            subtitle = "Tap to change. Always locks when the screen turns off.",
                            value = when (prefs.vaultAutoLockMs) {
                                0L -> "Immediately"
                                30_000L -> "30 seconds"
                                60_000L -> "1 minute"
                                else -> "5 minutes"
                            },
                            onClick = { viewModel.cycleAutoLock(prefs.vaultAutoLockMs) }
                        )
                    }
                }
            }

            if (show(com.lnxv.player.core.model.SettingsSection.ADVANCED)) item {
                SectionHeader("Advanced > Experimental")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        com.lnxv.player.core.flags.FeatureFlag.values().forEachIndexed { index, flag ->
                            if (index > 0) GlassDivider()
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

            if (show(com.lnxv.player.core.model.SettingsSection.STORAGE)) item {
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
                        SettingActionRow("Clear thumbnails") {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val loader = coil.Coil.imageLoader(ctx)
                                loader.memoryCache?.clear()
                                loader.diskCache?.clear()
                                refresh()
                            }
                        }
                        GlassDivider()
                        SettingActionRow("Clear cache (thumbnails, downloaded updates, temp subtitles)") {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                coil.Coil.imageLoader(ctx).memoryCache?.clear()
                                com.lnxv.player.core.util.StorageUtil.clearContents(ctx.cacheDir)
                                refresh()
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Your videos and the encrypted vault are never touched by these actions.", color = appColors.textTertiary, fontSize = 11.sp)
                    }
                }
            }

            if (show(com.lnxv.player.core.model.SettingsSection.PRIVACY)) item {
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
                                            viewModel.beginBiometricEnroll(a)
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
                                "The only network requests are the update check to GitHub (sideload build), streams you open yourself, and OpenSubtitles when you tap Search or Download for subtitles (sideload build, with your own API key).",
                            color = appColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (show(com.lnxv.player.core.model.SettingsSection.ABOUT)) item {
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

            if (onlineSubsEnabled &&
                show(com.lnxv.player.core.model.SettingsSection.ONLINE_SUBTITLES)
            ) item {
                SectionHeader(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_os_header))
                OnlineSubtitlesSettingsPanel()
            }

            if (show(com.lnxv.player.core.model.SettingsSection.LICENSES)) item {
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenLicenses)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_open_source_licenses),
                            color = appColors.textPrimary,
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.settings_open_source_licenses_summary),
                            color = appColors.textTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (show(com.lnxv.player.core.model.SettingsSection.DEVELOPER)) item {
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
                        GlassDialogButton(text = "Copy system info", primary = true, onClick = {
                            val info = "LNXV ${viewModel.currentVersion} | Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) | " +
                                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} | ${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "?"} | effects=${if (reduceEffects) "reduced" else "full"}"
                            val cm = settingsContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("LNXV system info", info))
                        })
                        GlassDialogButton(text = "Reset flags", destructive = true, onClick = { confirmResetFlags = true })
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
        GlassStepper(onMinus = onMinus, onPlus = onPlus)
    }
}

/** iOS row: title + optional subtitle on the left, current value and a chevron on the right; the whole row is tappable. */
@Composable
private fun SettingValueRow(title: String, subtitle: String, value: String, onClick: () -> Unit) {
    val tap = com.lnxv.player.core.design.rememberTapHaptic()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(pressedScale = 0.99f) { tap(); onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = appColors.textTertiary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = value, color = appColors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(text = "  ›", color = appColors.textTertiary, fontSize = 18.sp)
    }
}

/** Tappable accent action row (no value). */
@Composable
private fun SettingActionRow(title: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        text = title,
        color = if (destructive) DestructiveRed else appColors.accent,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(pressedScale = 0.99f) { onClick() }
            .padding(vertical = 6.dp)
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = appColors.textTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp, start = 12.dp, end = 4.dp)
    )
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tap = com.lnxv.player.core.design.rememberTapHaptic()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { tap(); onCheckedChange(!isChecked) },
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
