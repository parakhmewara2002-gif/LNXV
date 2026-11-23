package com.lnxv.player.core.webdav

import androidx.media3.datasource.DataSpec
import java.net.URI

/**
 * Session-only Basic credentials for one WebDAV server. Nothing is written to disk. The Authorization header is added
 * only to requests for exactly the same scheme, host and port, and only over HTTPS.
 */
object WebDavAuth {
    @Volatile private var origin: String? = null
    @Volatile private var header: String? = null

    fun basicHeader(user: String, password: String): String {
        val bytes = "$user:$password".toByteArray(Charsets.UTF_8)
        return try {
            "Basic " + base64(bytes)
        } finally {
            bytes.fill('\u0000')
        }
    }

    // java.util.Base64 needs API 26 and android.util.Base64 is not available in JVM unit tests; this is 10 lines.
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    internal fun base64(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            sb.append(ALPHABET[b0 shr 2])
            sb.append(ALPHABET[((b0 and 3) shl 4) or (b1 shr 4)])
            sb.append(if (i + 1 < data.size) ALPHABET[((b1 and 15) shl 2) or (b2 shr 6)] else '=')
            sb.append(if (i + 2 < data.size) ALPHABET[b2 and 63] else '=')
            i += 3
        }
        return sb.toString()
    }

    /** `https://host:port` (lower-case host, default port made explicit); null for non-https or malformed URLs. */
    fun originOf(url: String): String? = try {
        val u = URI(url)
        if (u.scheme?.lowercase() != "https" || u.host.isNullOrEmpty()) null
        else "https://${u.host.lowercase()}:${if (u.port == -1) 443 else u.port}"
    } catch (e: Exception) { null }

    fun start(serverUrl: String, user: String, password: String): Boolean {
        val o = originOf(serverUrl) ?: return false
        origin = o
        header = basicHeader(user, password)
        return true
    }

    fun clear() { origin = null; header = null }

    val active: Boolean get() = origin != null

    /** Header to send for [url], or null when it is not the registered server. */
    fun headerFor(url: String): String? {
        val o = origin ?: return null
        return if (originOf(url) == o) header else null
    }

    /** Resolver for `ResolvingDataSource`: adds the header to matching requests, leaves everything else untouched. */
    fun resolve(spec: DataSpec): DataSpec {
        val h = headerFor(spec.uri.toString()) ?: return spec
        return spec.withRequestHeaders(mapOf("Authorization" to h))
    }
}
