package com.lnxv.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
        containerColor = Color.Black,
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
                    color = Color.White,
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
                    backgroundColor = Color(0x261E293B)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingSwitchRow(
                            title = "AMOLED True Black",
                            subtitle = "Enhance contrast and save battery on OLED screens",
                            isChecked = prefs.isAmoled,
                            onCheckedChange = { viewModel.toggleAmoled(it) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Liquid Glass Blur: ${prefs.glassBlurIntensity.toInt()}dp",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Slider(
                            value = prefs.glassBlurIntensity,
                            onValueChange = { viewModel.updateGlassBlur(it) },
                            valueRange = 4f..32f,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan
                            )
                        )
                    }
                }
            }

            item {
                SectionHeader("Playback & Gestures")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x261E293B)
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
                    }
                }
            }

            item {
                SectionHeader("Updates")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x261E293B)
                ) {
                    val st = updateState
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = NeonCyan
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Version ${viewModel.currentVersion}",
                                    color = Color.White,
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
                                    color = Color(0x99FFFFFF),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (st is UpdateState.Downloading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { st.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = NeonCyan
                            )
                        }
                        if (st !is UpdateState.Checking && st !is UpdateState.Downloading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (st is UpdateState.Available) viewModel.downloadAndInstall(st.info)
                                        else viewModel.checkForUpdate()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (st is UpdateState.Available) "Download & install" else "Check for updates",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("About LNXV")
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x261E293B)
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
                            tint = NeonCyan
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "LNXV Video Player v${viewModel.currentVersion}",
                                color = Color.White,
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "AndroidX Media3 ExoPlayer • Jetpack Compose • Clean Architecture",
                                color = Color(0x80FFFFFF),
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
        color = NeonCyan,
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
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = Color(0x80FFFFFF), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = NeonCyan,
                uncheckedThumbColor = Color(0x80FFFFFF),
                uncheckedTrackColor = Color(0x33FFFFFF)
            )
        )
    }
}
