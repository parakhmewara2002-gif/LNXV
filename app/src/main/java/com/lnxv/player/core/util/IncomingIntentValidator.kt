package com.lnxv.player.core.util

/**
 * Validates VIEW intents before any media is opened: only content:// and file:// URIs that look like video.
 * Pure string logic so it can be unit tested without Android classes.
 */
object IncomingIntentValidator {
    private val ALLOWED_SCHEMES = setOf("content", "file")

    /** True when [path] equals or lies inside any of [roots] (canonical paths). Used to refuse file:// into app-private dirs. */
    fun isUnderAny(path: String, roots: List<String>): Boolean =
        roots.any { path == it || path.startsWith(it.trimEnd('/') + "/") }

    fun isAllowedViewData(scheme: String?, mimeType: String?): Boolean {
        if (scheme == null || scheme.lowercase() !in ALLOWED_SCHEMES) return false
        val mime = mimeType?.lowercase()?.trim()
        return mime.isNullOrEmpty() || mime.startsWith("video/") || mime == "application/x-matroska"
    }
}
