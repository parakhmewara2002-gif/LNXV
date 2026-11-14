package com.lnxv.player.ui.player

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.media3.ui.PlayerView
import com.lnxv.player.core.model.PlaybackState

/**
 * Items of the player's overflow ("more") menu, extracted from PlayerScreen (Phase 4 split).
 * Pure UI: every state change goes through [viewModel] or the callbacks.
 */
@Composable
fun PlayerMoreMenuItems(
    viewModel: PlayerViewModel,
    uiState: PlayerUiState,
    playbackState: PlaybackState,
    queueUi: QueueUi,
    audioDelayMs: Long,
    context: Context,
    playerView: PlayerView?,
    videoRotation: Int,
    videoFlip: Boolean,
    closeMenu: () -> Unit,
    openSleepTimer: () -> Unit,
    onRotateVideo: () -> Unit,
    onFlipVideo: () -> Unit
) {
        PlayerMenuItem(
            text = { Text(if (playbackState.sleepTimerRemainingSeconds != null) "Sleep Timer (active)" else "Sleep Timer") },
            onClick = {
                openSleepTimer()
                closeMenu()
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
        if (viewModel.userPrefs.collectAsState().value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.EQUALIZER)) {
            val eqPreset by viewModel.eqPreset.collectAsState()
            PlayerMenuItem(
                text = { Text("Equalizer: ${eqPreset.label} (tap to change)") },
                onClick = { viewModel.cycleEqPreset() }
            )
            val boost by viewModel.boostMb.collectAsState()
            PlayerMenuItem(
                text = { Text("Volume boost: ${if (boost == 0) "off" else "+${boost / 100} dB"} (tap to change)") },
                onClick = { viewModel.cycleBoost() }
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
                closeMenu()
            }
        )
        PlayerMenuItem(
            text = { Text("Next frame") },
            onClick = {
                viewModel.stepFrame(true)
                closeMenu()
            }
        )
        if (viewModel.userPrefs.collectAsState().value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.VIDEO_TRANSFORMS)) {
            PlayerMenuItem(
                text = { Text("Rotate video 90° (now $videoRotation°)") },
                onClick = onRotateVideo
            )
            PlayerMenuItem(
                text = { Text(if (videoFlip) "Flip horizontal (on)" else "Flip horizontal") },
                onClick = onFlipVideo
            )
        }
        PlayerMenuItem(
            text = { Text("Screenshot") },
            onClick = {
                closeMenu()
                // Never capture frames of vault videos.
                if (uiState.currentVideo?.isVault == true) {
                    android.widget.Toast.makeText(context, "Screenshots are disabled for vault videos", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    playerView?.let { ScreenshotHelper.capture(context, it) }
                }
            }
        )
        PlayerMenuItem(
            text = { Text("Set Point A (AB Repeat)") },
            onClick = {
                viewModel.setPointA()
                closeMenu()
            }
        )
        PlayerMenuItem(
            text = { Text("Set Point B (AB Repeat)") },
            onClick = {
                viewModel.setPointB()
                closeMenu()
            }
        )
        PlayerMenuItem(
            text = { Text("Clear AB Repeat") },
            onClick = {
                viewModel.clearABRepeat()
                closeMenu()
            }
        )
        PlayerMenuItem(
            text = { Text("Video Properties") },
            onClick = {
                viewModel.showPropertiesDialog(true)
                closeMenu()
            }
        )
}
