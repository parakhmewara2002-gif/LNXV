package com.lnxv.player.core.util

/**
 * Validates VIEW intents before any media is opened: only content:// and file:// URIs that look like video.
 * Pure string logic so it can be unit tested without Android classes.
 */
object IncomingIntentValidator {
    private val ALLOWED_SCHEMES = setOf("content", "file")

    fun isAllowedViewData(scheme: String?, mimeType: String?): Boolean {
        if (scheme == null || scheme.lowercase() !in ALLOWED_SCHEMES) return false
        val mime = mimeType?.lowercase()?.trim()
        return mime.isNullOrEmpty() || mime.startsWith("video/") || mime == "application/x-matroska"
    }
}
