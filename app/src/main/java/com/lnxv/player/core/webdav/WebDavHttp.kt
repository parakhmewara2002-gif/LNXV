package com.lnxv.player.core.webdav

import java.io.ByteArrayOutputStream

/**
 * Minimal HTTP/1.1 message handling for PROPFIND. HttpURLConnection refuses PROPFIND, and adding an HTTP client
 * dependency for one request is not worth it. Pure functions here are unit tested; the socket part is in [WebDavClient].
 */
object WebDavHttp {
    const val MAX_BODY = 2 * 1024 * 1024

    fun buildPropfind(host: String, path: String, authHeader: String?): ByteArray {
        val safePath = path.ifEmpty { "/" }.replace("\r", "").replace("\n", "")
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>"""
        val sb = StringBuilder()
        sb.append("PROPFIND ").append(safePath).append(" HTTP/1.1\r\n")
        sb.append("Host: ").append(host).append("\r\n")
        sb.append("Depth: 1\r\n")
        sb.append("Content-Type: application/xml\r\n")
        sb.append("Content-Length: ").append(body.toByteArray().size).append("\r\n")
        sb.append("Accept: application/xml, text/xml\r\n")
        sb.append("Connection: close\r\n")
        if (authHeader != null) sb.append("Authorization: ").append(authHeader).append("\r\n")
        sb.append("\r\n").append(body)
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    data class Response(val status: Int, val headers: Map<String, String>, val body: ByteArray)

    /** Parses a full response read until the connection closed. Handles Content-Length and chunked bodies. */
    fun parseResponse(raw: ByteArray): Response {
        val split = indexOf(raw, "\r\n\r\n".toByteArray())
        require(split > 0) { "Incomplete response" }
        val head = String(raw, 0, split, Charsets.ISO_8859_1).split("\r\n")
        val status = head[0].split(' ').getOrNull(1)?.toIntOrNull() ?: throw IllegalArgumentException("Bad status line")
        val headers = HashMap<String, String>()
        for (line in head.drop(1)) {
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val bodyStart = split + 4
        val bodyBytes = raw.copyOfRange(bodyStart, raw.size)
        val body = if (headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) dechunk(bodyBytes) else {
            val len = headers["content-length"]?.toIntOrNull()
            if (len != null && len in 0..bodyBytes.size) bodyBytes.copyOfRange(0, len) else bodyBytes
        }
        return Response(status, headers, body)
    }

    fun dechunk(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (pos < data.size) {
            val lineEnd = indexOf(data, "\r\n".toByteArray(), pos)
            require(lineEnd >= 0) { "Bad chunk" }
            val size = String(data, pos, lineEnd - pos, Charsets.ISO_8859_1).substringBefore(';').trim().toIntOrNull(16)
                ?: throw IllegalArgumentException("Bad chunk size")
            if (size == 0) break
            val start = lineEnd + 2
            require(start + size <= data.size) { "Truncated chunk" }
            out.write(data, start, size)
            pos = start + size + 2
        }
        return out.toByteArray()
    }

    private fun indexOf(h: ByteArray, n: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..h.size - n.size) {
            for (j in n.indices) if (h[i + j] != n[j]) continue@outer
            return i
        }
        return -1
    }
}
