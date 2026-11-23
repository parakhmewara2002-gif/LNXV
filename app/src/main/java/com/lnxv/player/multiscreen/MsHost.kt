package com.lnxv.player.multiscreen

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

data class HostUi(
    val running: Boolean = false,
    val ip: String? = null,
    val port: Int = 0,
    val pin: String = "",
    val clientName: String? = null,
    val clientW: Float = 0f,
    val clientH: Float = 0f,
    val mode: MsMode = MsMode.MIRROR,
    val hostMuted: Boolean = false,
    val hostW: Float = 0f,
    val hostH: Float = 0f,
    val aspect: Float = 16f / 9f,
    val note: String? = null
) {
    val connected: Boolean get() = clientName != null
}

/** Process-wide handle so the player screen and its dialog share one session. */
object MultiScreen {
    val hostFlow = MutableStateFlow<MsHost?>(null)

    fun startHost(context: Context, player: ExoPlayer): MsHost? {
        hostFlow.value?.let { return it }
        val h = MsHost(context.applicationContext, player)
        if (!h.start()) return null
        hostFlow.value = h
        return h
    }

    fun stopHost() {
        val h = hostFlow.value ?: return
        hostFlow.value = null
        h.stop()
    }
}

/**
 * Hosts one follower phone: HTTP range server for the current local video, a JSON-lines control channel,
 * and a 500 ms state broadcast. Everything that touches the player runs on the main thread; sockets run on
 * background threads.
 */
class MsHost(private val context: Context, private val player: ExoPlayer) {
    private val main = Handler(Looper.getMainLooper())
    // Bound concurrent socket handlers and queued handshakes; LAN peers must not create unbounded threads.
    private val pool: ExecutorService = ThreadPoolExecutor(
        4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8), ThreadPoolExecutor.AbortPolicy()
    )
    private val _ui = MutableStateFlow(HostUi())
    val ui: StateFlow<HostUi> = _ui.asStateFlow()

    @Volatile private var closed = false
    private var server: ServerSocket? = null
    @Volatile private var client: Client? = null
    @Volatile private var mediaUri: Uri? = null
    @Volatile private var token: String = ""
    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private val originalVolume = player.volume
    private data class AuthWindow(val startedAtMs: Long, var failures: Int)
    private val failedPinAttempts = ConcurrentHashMap<String, AuthWindow>()

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { refreshMedia(); broadcastState() }
        override fun onIsPlayingChanged(isPlaying: Boolean) = broadcastState()
        override fun onPlaybackStateChanged(playbackState: Int) = broadcastState()
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = broadcastState()
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) = broadcastState()
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _ui.update { it.copy(aspect = videoAspect(videoSize)) }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (closed) return
            broadcastState()
            main.postDelayed(this, 500L)
        }
    }

    // ---- lifecycle -------------------------------------------------------------------------------------

    fun start(): Boolean {
        return try {
            val ss = ServerSocket(0)
            server = ss
            val pin = (100000 + SecureRandom().nextInt(900000)).toString()
            token = randomToken()
            _ui.value = _ui.value.copy(running = true, ip = localLanIp(), port = ss.localPort, pin = pin, hostMuted = _ui.value.mode == MsMode.SPEAKER)
            pool.execute { acceptLoop(ss) }
            main.post {
                player.addListener(playerListener)
                refreshMedia()
                applyHostMute()
                main.postDelayed(ticker, 500L)
            }
            registerNsd(ss.localPort)
            true
        } catch (e: Exception) {
            _ui.value = HostUi(note = "Could not start: ${e.message}")
            false
        }
    }

    fun stop() {
        if (closed) return
        closed = true
        main.removeCallbacksAndMessages(null)
        main.post {
            try { player.removeListener(playerListener) } catch (_: Exception) {}
            try { player.volume = originalVolume } catch (_: Exception) {}
        }
        try { client?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        try { regListener?.let { nsd?.unregisterService(it) } } catch (_: Exception) {}
        pool.shutdownNow()
        _ui.value = HostUi()
    }

    // ---- configuration (called from UI, main thread) ---------------------------------------------------

    fun setMode(m: MsMode) {
        _ui.update { it.copy(mode = m, hostMuted = m == MsMode.SPEAKER) }
        applyHostMute(); sendConfig()
    }
    fun setHostMuted(v: Boolean) { _ui.update { it.copy(hostMuted = v) }; applyHostMute() }
    fun setScreenDp(w: Float, h: Float) {
        val lw = maxOf(w, h); val lh = minOf(w, h)
        val cur = _ui.value
        if (cur.hostW != lw || cur.hostH != lh) { _ui.update { it.copy(hostW = lw, hostH = lh) }; sendConfig() }
    }
    fun disconnectClient() { client?.close() }

    private fun applyHostMute() {
        try { player.volume = if (_ui.value.hostMuted) 0f else originalVolume } catch (_: Exception) {}
    }

    // ---- media -----------------------------------------------------------------------------------------

    private fun refreshMedia() {
        val uri = player.currentMediaItem?.localConfiguration?.uri
        if (uri != mediaUri) {
            mediaUri = uri
            token = randomToken()
        }
        val note = when {
            uri == null -> null
            isLocalMedia(uri) || isNetworkMedia(uri) -> null
            else -> "This video type (vault / network share) can't be shared."
        }
        _ui.update { it.copy(note = note) }
        sendMedia()
    }

    private fun mediaJson(): JSONObject {
        val uri = mediaUri
        val j = JSONObject().put("t", "media")
        when {
            uri == null -> j.put("url", "")
            isNetworkMedia(uri) -> j.put("url", uri.toString()).put("local", false)
            isLocalMedia(uri) -> j.put("url", MsProto.MEDIA_PREFIX + token).put("local", true)
            else -> j.put("url", "")
        }
        j.put("title", player.mediaMetadata.title?.toString().orEmpty())
        return j
    }

    private fun sendMedia() { client?.send(mediaJson()) }

    private fun configJson(): JSONObject {
        val u = _ui.value
        val c = client
        return JSONObject().put("t", "cfg").put("mode", u.mode.name)
            .put("hostW", u.hostW.toDouble()).put("hostH", u.hostH.toDouble())
            .put("connected", c != null)
    }

    private fun sendConfig() { client?.send(configJson()) }

    private fun broadcastState() {
        val c = client ?: return
        val playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        c.send(
            JSONObject().put("t", "st").put("pos", player.currentPosition).put("play", playing)
                .put("speed", player.playbackParameters.speed.toDouble()).put("hostNow", SystemClock.elapsedRealtime())
                .put("ended", player.playbackState == Player.STATE_ENDED)
        )
    }

    // ---- sockets ---------------------------------------------------------------------------------------

    private fun acceptLoop(ss: ServerSocket) {
        while (!closed) {
            val s = try { ss.accept() } catch (e: Exception) { return }
            try { pool.execute { handle(s) } } catch (e: Exception) { try { s.close() } catch (_: Exception) {} }
        }
    }

    private fun handle(s: Socket) {
        try {
            s.soTimeout = 10000
            val input = BufferedInputStream(s.getInputStream())
            val first = readAsciiLine(input)
            if (first == null) { s.close(); return }
            if (first.startsWith("GET ")) serveHttp(s, input, first) else control(s, input, first)
        } catch (e: Exception) {
            try { s.close() } catch (_: Exception) {}
        }
    }

    private fun serveHttp(s: Socket, input: InputStream, requestLine: String) {
        s.use { sock ->
            val path = requestLine.split(" ").getOrNull(1).orEmpty()
            var rangeHeader: String? = null
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) rangeHeader = line.substringAfter(':').trim()
            }
            val out = sock.getOutputStream()
            val uri = mediaUri
            if (uri == null || !isLocalMedia(uri) || path != MsProto.MEDIA_PREFIX + token || client == null) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush(); return
            }
            val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
            if (afd == null) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush(); return
            }
            afd.use { a ->
                val total = if (a.declaredLength >= 0) a.declaredLength else a.parcelFileDescriptor.statSize
                var start = 0L
                var end = total - 1
                var partial = false
                val r = rangeHeader
                if (r != null && r.startsWith("bytes=")) {
                    val spec = r.removePrefix("bytes=").substringBefore(',')
                    val a0 = spec.substringBefore('-').trim()
                    val b0 = spec.substringAfter('-', "").trim()
                    if (a0.isEmpty()) {
                        val n = b0.toLongOrNull() ?: 0L
                        start = (total - n).coerceAtLeast(0L)
                    } else {
                        start = a0.toLongOrNull() ?: 0L
                        if (b0.isNotEmpty()) end = (b0.toLongOrNull() ?: end).coerceAtMost(total - 1)
                    }
                    partial = true
                }
                if (start >= total || start > end) {
                    out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush(); return
                }
                val len = end - start + 1
                val mime = context.contentResolver.getType(uri) ?: "video/*"
                val head = StringBuilder()
                head.append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                head.append("Content-Type: $mime\r\nAccept-Ranges: bytes\r\nContent-Length: $len\r\n")
                if (partial) head.append("Content-Range: bytes $start-$end/$total\r\n")
                head.append("Connection: close\r\n\r\n")
                out.write(head.toString().toByteArray())
                val fis = FileInputStream(a.fileDescriptor)
                fis.channel.position(a.startOffset + start)
                copyN(fis, out, len)
                out.flush()
            }
        }
    }

    private fun copyN(src: InputStream, dst: OutputStream, count: Long) {
        val buf = ByteArray(64 * 1024)
        var left = count
        while (left > 0) {
            val n = src.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            dst.write(buf, 0, n)
            left -= n
        }
    }

    private fun recordPinAttempt(ip: String, success: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        var blocked = false
        failedPinAttempts.compute(ip) { _, previous ->
            val window = if (previous == null || now - previous.startedAtMs > 60_000L) {
                AuthWindow(now, 0)
            } else {
                previous
            }
            if (window.failures >= 5) {
                blocked = true
                window
            } else if (success) {
                null
            } else {
                window.failures++
                window
            }
        }
        if (failedPinAttempts.size > 128) {
            failedPinAttempts.entries.removeIf { now - it.value.startedAtMs > 60_000L }
        }
        return !blocked
    }

    private fun control(s: Socket, input: InputStream, firstLine: String) {
        val out = s.getOutputStream()
        val hello = try { JSONObject(firstLine) } catch (e: Exception) { s.close(); return }
        fun reply(j: JSONObject) { out.write((j.toString() + "\n").toByteArray()); out.flush() }
        val peerIp = s.inetAddress?.hostAddress.orEmpty()
        val isHello = hello.optString("t") == "hello"
        val pinMatches = isHello && hello.optString("pin") == _ui.value.pin
        if (!recordPinAttempt(peerIp, pinMatches) || !pinMatches) {
            reply(JSONObject().put("t", "deny")); s.close(); return
        }
        if (client != null) { reply(JSONObject().put("t", "busy")); s.close(); return }
        val c = Client(s, out)
        client = c
        _ui.update {
            it.copy(clientName = hello.optString("name", "Phone"), clientW = hello.optDouble("w", 0.0).toFloat(), clientH = hello.optDouble("h", 0.0).toFloat())
        }
        c.send(JSONObject().put("t", "ok"))
        main.post { sendConfig(); sendMedia(); broadcastState() }
        try {
            while (!closed) {
                val line: String? = try { readAsciiLine(input) } catch (e: SocketTimeoutException) { null }
                if (line == null) break
                val j = try { JSONObject(line) } catch (e: Exception) { continue }
                when (j.optString("t")) {
                    "ping" -> c.send(JSONObject().put("t", "pong").put("c", j.optLong("c")).put("h", SystemClock.elapsedRealtime()))
                    "bye" -> break
                }
            }
        } finally {
            c.close()
            if (client === c) client = null
            _ui.update { it.copy(clientName = null, clientW = 0f, clientH = 0f) }
        }
    }

    private inner class Client(val socket: Socket, val out: OutputStream) {
        private val writer: ExecutorService = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(32), ThreadPoolExecutor.AbortPolicy()
        )
        fun send(j: JSONObject) {
            try {
                writer.execute {
                    try { out.write((j.toString() + "\n").toByteArray()); out.flush() } catch (e: IOException) { close() }
                }
            } catch (_: Exception) {
                // A slow or stalled peer must not create an unbounded outbound queue.
                close()
            }
        }
        fun close() {
            try { socket.close() } catch (_: Exception) {}
            try { writer.shutdownNow() } catch (_: Exception) {}
        }
    }

    // ---- discovery -------------------------------------------------------------------------------------

    private fun registerNsd(port: Int) {
        try {
            val mgr = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsd = mgr
            val info = NsdServiceInfo().apply {
                serviceName = "LNXV " + (Build.MODEL ?: "phone")
                serviceType = MsProto.SERVICE_TYPE
                setPort(port)
            }
            val l = object : NsdManager.RegistrationListener {
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            }
            regListener = l
            mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            // Discovery is optional: the other phone can still type the address.
        }
    }

    private fun randomToken(): String {
        val b = ByteArray(12)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}
