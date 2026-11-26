package com.lnxv.player.core.session

import com.lnxv.player.core.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide, in-memory hand-off state between screens (never persisted).
 * Owned by LNXVApplication and exposed through Koin so ViewModels receive it via their constructors
 * and tests can pass a fresh instance instead of touching the Application.
 */
class SessionState {
    /** Transient "Open with" video (never written to Room); played via route id TRANSIENT_VIDEO_ID. */
    @Volatile var transientVideo: VideoItem? = null

    /** Explicit play queue (e.g. a playlist) consumed once by the next player session; null = use the folder queue. */
    @Volatile var queueOverride: List<Long>? = null

    /** Set when a VIEW intent was accepted; consumed (set back to null) by the nav host. */
    val pendingOpenVideoId = MutableStateFlow<Long?>(null)

    /** True while a vault video is loaded in the shared player (drives FLAG_SECURE and lock handling). */
    @Volatile var playingVaultItem: Boolean = false

    /** Saves the current playback position; installed by PlayerViewModel and used when the app goes to background. */
    @Volatile var progressSaver: (() -> Unit)? = null
}
