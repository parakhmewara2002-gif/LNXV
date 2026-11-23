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
    onFlipVideo: () -> Unit,
    onMultiScreen: () -> Unit = {}
) {
        PlayerMenuItem(
            text = { Text("Second screen (Wi-Fi)") },
            onClick = {
                closeMenu()
                onMultiScreen()
            }
        )
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
        if (viewModel.userPrefs.collectAsState().value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.AUDIO_ONLY_MODE)) {
        val audioOnly by viewModel.audioOnly.collectAsState()
        PlayerMenuItem(
            text = { Text(if (audioOnly) "Audio only: on (tap for video)" else "Audio only") },
            onClick = {
                viewModel.toggleAudioOnly()
                closeMenu()
            }
        )
    }
    if (viewModel.userPrefs.collectAsState().value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.EQUALIZER)) {
            val eqPreset by viewModel.eqPreset.collectAsState()
            PlayerMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_audio_fx, eqPreset.label)) },
                onClick = {
                    closeMenu()
                    viewModel.showAudioFxDialog(true)
                }
            )
            val boost by viewModel.boostMb.collectAsState()
            PlayerMenuItem(
                text = {
                    val pct = viewModel.boostPercent(boost)
                    Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_volume_boost, if (pct <= 100) "off" else "$pct%"))
                },
                onClick = { viewModel.cycleBoost() }
            )
        }
        PlayerMenuItem(
            text = { Text("Audio delay +50 ms (now $audioDelayMs ms)") },
            onClick = { viewModel.adjustAudioDelay(50L) }
        )
        PlayerMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_audio_delay_minus)) },
            onClick = { viewModel.adjustAudioDelay(-50L) }
        )
        PlayerMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_prev_frame)) },
            onClick = {
                viewModel.stepFrame(false)
                closeMenu()
            }
        )
        PlayerMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_next_frame)) },
            onClick = {
                viewModel.stepFrame(true)
                closeMenu()
            }
        )
        if (androidx.compose.ui.res.booleanResource(com.lnxv.player.R.bool.popup_window_enabled) &&
            viewModel.userPrefs.collectAsState().value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.POPUP_WINDOW) &&
            playerView?.player != null &&
            uiState.currentVideo?.isVault != true
        ) {
            PlayerMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_popup)) },
                onClick = {
                    closeMenu()
                    val p = playerView?.player
                    if (p == null) return@PlayerMenuItem
                    if (!com.lnxv.player.playback.popup.PopupWindowController.canShow(context)) {
                        android.widget.Toast.makeText(context, context.getString(com.lnxv.player.R.string.popup_need_permission), android.widget.Toast.LENGTH_LONG).show()
                        com.lnxv.player.playback.popup.PopupWindowController.requestPermission(context)
                    } else if (com.lnxv.player.playback.popup.PopupWindowController.show(context, p)) {
                        (context as? android.app.Activity)?.moveTaskToBack(true)
                    }
                }
            )
        }
        val castBackend = com.lnxv.player.core.cast.CastBackends.instance
        val castUrl = uiState.currentVideo?.uri?.toString()
        if (castBackend != null &&
            viewModel.userPrefs.collectAsState().value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.CAST) &&
            com.lnxv.player.core.cast.CastPolicy.canCast(castUrl)
        ) {
            PlayerMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_cast)) },
                onClick = {
                    closeMenu()
                    val url = castUrl ?: return@PlayerMenuItem
                    castBackend.cast(context, url, uiState.currentVideo?.title.orEmpty(), viewModel.positionMs.value) {
                        playerView?.player?.pause()
                    }
                }
            )
        }
        val chapterList by viewModel.chapters.collectAsState()
        if (viewModel.userPrefs.collectAsState().value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.CHAPTERS) && chapterList.isNotEmpty()) {
            PlayerMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_chapters)) },
                onClick = {
                    closeMenu()
                    viewModel.showChaptersDialog(true)
                }
            )
        }
        if (viewModel.userPrefs.collectAsState().value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.BOOKMARKS) && viewModel.canBookmark) {
            PlayerMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_bookmarks)) },
                onClick = {
                    closeMenu()
                    viewModel.showBookmarksDialog(true)
                }
            )
        }
        if (viewModel.userPrefs.collectAsState().value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.PICTURE_ENHANCER)) {
            PlayerMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_picture)) },
                onClick = {
                    closeMenu()
                    viewModel.showPictureDialog(true)
                }
            )
        }
        if (viewModel.userPrefs.collectAsState().value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.VIDEO_TRANSFORMS)) {
            PlayerMenuItem(
                text = { Text("Rotate video 90° (now $videoRotation°)") },
                onClick = onRotateVideo
            )
            PlayerMenuItem(
                text = { Text(if (videoFlip) "Flip horizontal (on)" else "Flip horizontal") },
                onClick = onFlipVideo
            )
            val vc = viewModel.videoColor.collectAsState().value
            PlayerMenuItem(
                text = { Text("Brightness: %+.0f%%".format(vc.brightness * 100)) },
                onClick = { viewModel.setVideoColor(vc.copy(brightness = com.lnxv.player.core.model.VideoColor.cycle(vc.brightness))) }
            )
            PlayerMenuItem(
                text = { Text("Contrast: %+.0f%%".format(vc.contrast * 100)) },
                onClick = { viewModel.setVideoColor(vc.copy(contrast = com.lnxv.player.core.model.VideoColor.cycle(vc.contrast))) }
            )
            PlayerMenuItem(
                text = { Text("Saturation: %+.0f%%".format(vc.saturation * 100)) },
                onClick = { viewModel.setVideoColor(vc.copy(saturation = com.lnxv.player.core.model.VideoColor.cycle(vc.saturation))) }
            )
            if (!vc.isNeutral) PlayerMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_reset_colour)) },
                onClick = { viewModel.setVideoColor(com.lnxv.player.core.model.VideoColor()) }
            )
        }
        PlayerMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_screenshot)) },
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
            text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_point_a)) },
            onClick = {
                viewModel.setPointA()
                closeMenu()
            }
        )
        PlayerMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_point_b)) },
            onClick = {
                viewModel.setPointB()
                closeMenu()
            }
        )
        PlayerMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_clear_ab)) },
            onClick = {
                viewModel.clearABRepeat()
                closeMenu()
            }
        )
        PlayerMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.menu_properties)) },
            onClick = {
                viewModel.showPropertiesDialog(true)
                closeMenu()
            }
        )
}
