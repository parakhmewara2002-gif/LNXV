package com.lnxv.player.playback.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * Binds the app to [LNXVPlaybackService] through a MediaController so Media3 starts the foreground
 * notification (lock screen / Bluetooth / headset controls) while the shared player plays in the background.
 * Only connected while the "background play" setting is on; disconnecting lets the service stop itself.
 */
class BackgroundPlaybackConnector(private val context: Context) {
    private var future: ListenableFuture<MediaController>? = null

    @Synchronized
    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, LNXVPlaybackService::class.java))
        future = MediaController.Builder(context, token).buildAsync()
    }

    @Synchronized
    fun disconnect() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }
}
