package com.lnxv.player.multiscreen

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal HTTP/1.1 range client over a raw socket. Used only for the host phone on the local network:
 * the app's network security config forbids cleartext HTTP, and a raw socket is not subject to it, so the
 * stream stays on the LAN without loosening that policy for the rest of the app.
 */
@UnstableApi
class LanDataSource : BaseDataSource(true) {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var remaining = C.LENGTH_UNSET.toLong()
    private var currentUri: Uri? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        transferInitializing(dataSpec)
        val host = dataSpec.uri.host ?: throw IOException("missing host")
        val port = if (dataSpec.uri.port > 0) dataSpec.uri.port else 80
        val s = Socket()
        socket = s
        s.tcpNoDelay = true
        s.receiveBufferSize = 256 * 1024
        s.connect(InetSocketAddress(host, port), 5000)
        s.soTimeout = 10000
        val range = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            "bytes=${dataSpec.position}-${dataSpec.position + dataSpec.length - 1}"
        } else {
            "bytes=${dataSpec.position}-"
        }
        val req = "GET ${dataSpec.uri.encodedPath} HTTP/1.1\r\nHost: $host:$port\r\nRange: $range\r\nConnection: close\r\n\r\n"
        s.getOutputStream().apply { write(req.toByteArray()); flush() }
        val ins = BufferedInputStream(s.getInputStream(), 128 * 1024)
        input = ins
        val status = readAsciiLine(ins) ?: throw IOException("empty response")
        val code = status.split(" ").getOrNull(1)?.toIntOrNull() ?: throw IOException("bad status: $status")
        var contentLength = -1L
        while (true) {
            val line = readAsciiLine(ins) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0 && line.substring(0, idx).trim().equals("content-length", ignoreCase = true)) {
                contentLength = line.substring(idx + 1).trim().toLongOrNull() ?: -1L
            }
        }
        if (code != 200 && code != 206) throw IOException("HTTP $code")
        if (code == 200 && dataSpec.position > 0) {
            var toSkip = dataSpec.position
            while (toSkip > 0) {
                val n = ins.skip(toSkip)
                if (n <= 0) throw IOException("cannot skip")
                toSkip -= n
            }
            if (contentLength >= 0) contentLength -= dataSpec.position
        }
        remaining = if (contentLength >= 0) contentLength else C.LENGTH_UNSET.toLong()
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(remaining, length.toLong()).toInt()
        val n = input?.read(buffer, offset, toRead) ?: return C.RESULT_END_OF_INPUT
        if (n == -1) return C.RESULT_END_OF_INPUT
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        try {
            socket?.close()
        } catch (_: IOException) {
        } finally {
            socket = null
            input = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = LanDataSource()
    }
}
