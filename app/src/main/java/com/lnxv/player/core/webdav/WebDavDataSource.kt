package com.lnxv.player.core.webdav

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTPS-only WebDAV media reader. Redirects are deliberately disabled so the Basic Authorization header
 * can never be forwarded to another host by HttpURLConnection or Media3's default redirect handler.
 */
class WebDavDataSource : BaseDataSource(true) {
    private var connection: HttpURLConnection? = null
    private var input: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        try {
            val parsedUri = try { java.net.URI(dataSpec.uri.toString()) } catch (e: Exception) {
                throw IOException("Invalid WebDAV URL", e)
            }
            if (!parsedUri.scheme.equals("https", ignoreCase = true) || parsedUri.userInfo != null) {
                throw IOException("WebDAV playback requires a credential-free HTTPS URL")
            }
            if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET && dataSpec.httpMethod != DataSpec.HTTP_METHOD_HEAD) {
                throw IOException("Unsupported WebDAV request method")
            }
            val position = dataSpec.position
            val length = dataSpec.length
            if (position < 0 || (length != C.LENGTH_UNSET.toLong() && length < 0)) {
                throw IOException("Invalid WebDAV read range")
            }
            if (length > 0 && position > Long.MAX_VALUE - length + 1) {
                throw IOException("WebDAV read range overflow")
            }

            val conn = URL(dataSpec.uri.toString()).openConnection() as HttpURLConnection
            connection = conn
            conn.instanceFollowRedirects = false
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_HEAD) "HEAD" else "GET"
            conn.setRequestProperty("Accept-Encoding", "identity")
            dataSpec.httpRequestHeaders.forEach { (name, value) -> conn.setRequestProperty(name, value) }

            if (position != 0L || length != C.LENGTH_UNSET.toLong()) {
                val end = if (length == C.LENGTH_UNSET.toLong()) "" else (position + length - 1).toString()
                conn.setRequestProperty("Range", "bytes=$position-$end")
            }

            val status = conn.responseCode
            if (status in 300..399) throw IOException("WebDAV redirects are not allowed")
            if (status !in 200..299) throw IOException("WebDAV server returned HTTP $status")
            val contentLength = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()

            bytesRemaining = when (status) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    val range = conn.getHeaderField("Content-Range")
                        ?: throw IOException("WebDAV server returned an invalid byte range")
                    val match = CONTENT_RANGE.matchEntire(range.trim())
                        ?: throw IOException("WebDAV server returned an invalid byte range")
                    val start = match.groupValues[1].toLongOrNull()
                        ?: throw IOException("WebDAV server returned an invalid byte range")
                    val end = match.groupValues[2].toLongOrNull()
                        ?: throw IOException("WebDAV server returned an invalid byte range")
                    if (start != position || end < start) throw IOException("WebDAV server returned the wrong byte range")
                    val available = end - start + 1
                    if (length == C.LENGTH_UNSET.toLong()) available else minOf(length, available)
                }
                HttpURLConnection.HTTP_OK -> {
                    if (position > 0L) throw IOException("WebDAV server does not support byte-range seeking")
                    if (length == C.LENGTH_UNSET.toLong()) contentLength
                    else if (contentLength == C.LENGTH_UNSET.toLong()) length
                    else minOf(length, contentLength)
                }
                else -> if (length == C.LENGTH_UNSET.toLong()) contentLength else length
            }

            input = if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_HEAD) null else conn.inputStream
            opened = true
            transferStarted(dataSpec)
            return if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_HEAD) 0L else bytesRemaining
        } catch (e: Exception) {
            runCatching { close() }
            if (e is IOException) throw e
            throw IOException("WebDAV connection failed", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val source = input ?: return C.RESULT_END_OF_INPUT
        val readLength = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        val count = source.read(buffer, offset, readLength)
        if (count < 0) {
            if (bytesRemaining > 0L) throw EOFException("WebDAV response ended before the requested range")
            return C.RESULT_END_OF_INPUT
        }
        if (count == 0) return 0
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> = connection?.headerFields ?: emptyMap()

    override fun close() {
        val source = input
        input = null
        try {
            source?.close()
        } finally {
            connection?.disconnect()
            connection = null
            uri = null
            bytesRemaining = C.LENGTH_UNSET.toLong()
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private val CONTENT_RANGE = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+|\\*)", RegexOption.IGNORE_CASE)
    }
}
