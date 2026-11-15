package com.lnxv.player.core.util

import java.net.URI

/** Validation and sanitising for the "Open network stream" feature (pure JVM, unit tested). */
object StreamUrl {
    sealed class Result {
        data class Ok(val url: String) : Result()
        data class Error(val message: String) : Result()
    }

    private val SCHEMES = setOf("http", "https", "rtsp", "rtsps")

    fun validate(raw: String, allowCleartextHttp: Boolean): Result {
        val text = raw.trim()
        if (text.isEmpty()) return Result.Error("Enter a stream URL")
        val uri = try { URI(text) } catch (e: Exception) { return Result.Error("This is not a valid URL") }
        val scheme = uri.scheme?.lowercase() ?: return Result.Error("URL must start with https://, http:// or rtsp://")
        if (scheme !in SCHEMES) return Result.Error("Unsupported protocol \"$scheme\". Use HTTP(S), HLS, DASH or RTSP.")
        if (uri.host.isNullOrEmpty()) return Result.Error("URL has no host")
        if (scheme == "http" && !allowCleartextHttp) {
            return Result.Error("Plain HTTP is blocked for security. Use an https:// URL.")
        }
        return Result.Ok(text)
    }

    /** Removes `user:password@` so credentials are never stored in the recent-URL list. */
    fun stripCredentials(url: String): String = try {
        val u = URI(url)
        if (u.userInfo == null) url
        else URI(u.scheme, null, u.host, u.port, u.path, u.query, u.fragment).toString()
    } catch (e: Exception) { url }

    /** Hostname/last path segment as a title. */
    fun titleFor(url: String): String = try {
        val u = URI(url)
        val last = u.path?.trimEnd('/')?.substringAfterLast('/').orEmpty()
        if (last.isNotEmpty()) last else u.host ?: "Stream"
    } catch (e: Exception) { "Stream" }

    /** Most-recent-first list without duplicates, capped at [max]. */
    fun addRecent(existing: List<String>, newUrl: String, max: Int = 10): List<String> =
        (listOf(newUrl) + existing.filter { it != newUrl }).take(max)

    fun encodeList(list: List<String>): String = list.joinToString("\n")
    fun decodeList(s: String): List<String> = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}
