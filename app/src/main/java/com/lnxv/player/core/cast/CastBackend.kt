package com.lnxv.player.core.cast

import android.content.Context
import com.lnxv.player.core.webdav.WebDavAuth
import java.net.URI

/** Implemented by the sideload build only (the store build ships no Google Cast code). */
interface CastBackend {
    /** Starts casting [url]; asks the user to pick a device first when no session is active. */
    fun cast(context: Context, url: String, title: String, positionMs: Long, onStarted: () -> Unit)
    fun stop()
}

object CastBackends {
    val instance: CastBackend? by lazy { runCatching { CastBackendFactory.create() }.getOrNull() }
}

/** Which videos can be cast. A Chromecast fetches the URL itself, so it must be a plain, reachable http(s) address. */
object CastPolicy {
    fun canCast(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val u = try { URI(uri) } catch (e: Exception) { return false }
        val scheme = u.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (u.host.isNullOrEmpty() || u.userInfo != null) return false
        if (WebDavAuth.headerFor(uri) != null) return false // needs an Authorization header the receiver cannot send
        val host = u.host.lowercase()
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return false // the TV cannot reach the phone's loopback
        return true
    }
}
