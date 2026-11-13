package com.lnxv.player.ui.settings

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
import androidx.compose.runtime.LaunchedEffect
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

    LaunchedEffect(Unit) { viewModel.checkForUpdate() }

    Scaffold(
        modifier = Modifier.fillMaxSize().ambientBackground(),
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBackClick
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Settings",
                    color = appColors.textPrimary,
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader("Appearance & Glass Engine")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Theme",
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

            item {
                SectionHeader("Playback & Gestures")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                            title = "Brightness & Volume Gestures",
                            subtitle = "Swipe vertically on screen edges",
                            isChecked = prefs.isBrightnessGestureEnabled,
                            onCheckedChange = { viewModel.toggleBrightnessGesture(it) }
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
                                Text(text = "Resume Playback", color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(text = "Tap to change", color = appColors.textSecondary, fontSize = 12.sp)
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
                                Text(text = "Subtitle Text Size", color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Subtitle Color", color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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

            item {
                SectionHeader("Updates")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                            LinearProgressIndicator(
                                progress = { st.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = appColors.accent
                            )
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

            item {
                SectionHeader("Security")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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

            item {
                SectionHeader("Advanced > Experimental")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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

            item {
                SectionHeader("About LNXV")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = appColors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
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
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = appColors.accent,
                uncheckedThumbColor = appColors.textTertiary,
                uncheckedTrackColor = appColors.glassBorder
            )
        )
    }
}
