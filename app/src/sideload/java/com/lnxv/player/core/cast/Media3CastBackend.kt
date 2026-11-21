package com.lnxv.player.core.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.mediarouter.app.MediaRouteChooserDialog
import com.google.android.gms.cast.framework.CastContext

/** Casts a network URL with Media3's CastPlayer. Local files are not supported (they would need a local HTTP server). */
class Media3CastBackend : CastBackend {
    private class Pending(val url: String, val title: String, val positionMs: Long, val onStarted: () -> Unit)

    private var castContext: CastContext? = null
    private var castPlayer: CastPlayer? = null
    private var pending: Pending? = null

    private fun ensure(context: Context): CastPlayer? {
        castPlayer?.let { return it }
        val cc = runCatching { CastContext.getSharedInstance(context.applicationContext) }.getOrNull() ?: return null
        castContext = cc
        @Suppress("DEPRECATION")
        val cp = CastPlayer(cc)
        cp.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() { startPending() }
            override fun onCastSessionUnavailable() {}
        })
        castPlayer = cp
        return cp
    }

    private fun startPending() {
        val p = pending ?: return
        val cp = castPlayer ?: return
        if (!cp.isCastSessionAvailable) return
        pending = null
        val item = MediaItem.Builder().setUri(p.url).setMediaMetadata(MediaMetadata.Builder().setTitle(p.title).build()).build()
        cp.setMediaItem(item, p.positionMs)
        cp.prepare()
        cp.play()
        p.onStarted()
    }

    override fun cast(context: Context, url: String, title: String, positionMs: Long, onStarted: () -> Unit) {
        val cp = ensure(context) ?: return
        pending = Pending(url, title, positionMs, onStarted)
        if (cp.isCastSessionAvailable) {
            startPending()
        } else {
            val selector = castContext?.mergedSelector ?: return
            MediaRouteChooserDialog(context).apply { routeSelector = selector }.show()
        }
    }

    override fun stop() {
        pending = null
        castPlayer?.stop()
        castContext?.sessionManager?.endCurrentSession(true)
    }
}
