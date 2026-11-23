package com.lnxv.player.multiscreen

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

enum class RxStatus { IDLE, CONNECTING, CONNECTED, ERROR }

data class FoundHost(val name: String, val ip: String, val port: Int)

data class RxUi(
    val status: RxStatus = RxStatus.IDLE,
    val message: String? = null,
    val title: String = "",
    val mode: MsMode = MsMode.MIRROR,
    val hostW: Float = 0f,
    val hostH: Float = 0f,
    val aspect: Float = 16f / 9f,
    val delayMs: Int = 0,
    val found: List<FoundHost> = emptyList(),
    val hasMedia: Boolean = false
)

/** The follower phone: own ExoPlayer, fed from the host's HTTP range server and kept in sync over the control channel. */
@UnstableApi
class MsReceiver(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val _ui = MutableStateFlow(RxUi())
    val ui: StateFlow<RxUi> = _ui.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    2_500,  // minBufferMs (reduced from 50s to avoid latency on Wi-Fi)
                    15_000, // maxBufferMs
                    1_000,  // bufferForPlaybackMs
                    1_500   // bufferForPlaybackAfterRebufferMs
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        )
        .build().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true
        )
        setHandleAudioBecomingNoisy(true)
        setWakeMode(C.WAKE_MODE_NETWORK)
        addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _ui.update { it.copy(aspect = videoAspect(videoSize)) }
            }
        })
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var closed = false
    @Volatile private var hostIp = ""
    @Volatile private var hostPort = 0
    @Volatile private var offsetMs = 0L
    private var bestRtt = Long.MAX_VALUE
    private var lastSeekAt = 0L
    private var lastMediaUrl = ""
    private var out: java.io.OutputStream? = null
    private val writer = Executors.newSingleThreadExecutor()

    // ---- connect ---------------------------------------------------------------------------------------

    fun connect(ip: String, port: Int, pin: String, screenW: Float, screenH: Float) {
        disconnect(quiet = true)
        closed = false
        hostIp = ip.trim(); hostPort = port
        bestRtt = Long.MAX_VALUE; lastMediaUrl = ""
        _ui.update { it.copy(status = RxStatus.CONNECTING, message = null, hasMedia = false) }
        io.execute {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(hostIp, hostPort), 5000)
                s.soTimeout = 10000
                s.tcpNoDelay = true
                socket = s
                out = s.getOutputStream()
                sendRaw(
                    JSONObject().put("t", "hello").put("pin", pin.trim()).put("name", Build.MODEL ?: "Phone")
                        .put("w", maxOf(screenW, screenH).toDouble()).put("h", minOf(screenW, screenH).toDouble())
                )
                val input = BufferedInputStream(s.getInputStream())
                var firstReply = true
                var lastPing = 0L
                while (!closed) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastPing > 2000L) { lastPing = now; sendRaw(JSONObject().put("t", "ping").put("c", now)) }
                    val line: String? = try { readAsciiLine(input) } catch (e: SocketTimeoutException) { "" }
                    if (line == null) break
                    if (line.isEmpty()) continue
                    val j = try { JSONObject(line) } catch (e: Exception) { continue }
                    val t = j.optString("t")
                    if (firstReply) {
                        firstReply = false
                        when (t) {
                            "ok" -> _ui.update { it.copy(status = RxStatus.CONNECTED) }
                            "deny" -> { fail("Wrong PIN"); return@execute }
                            "busy" -> { fail("That phone already has a screen connected"); return@execute }
                            else -> { fail("Not an LNXV host"); return@execute }
                        }
                        continue
                    }
                    handle(j)
                }
                if (!closed) fail("Connection lost")
            } catch (e: Exception) {
                if (!closed) fail("Could not connect: ${e.message ?: "network error"}")
            }
        }
    }

    fun disconnect(quiet: Boolean = false) {
        closed = true
        try { sendRaw(JSONObject().put("t", "bye")) } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        main.post { try { player.stop(); player.clearMediaItems() } catch (_: Exception) {} }
        if (!quiet) _ui.update { it.copy(status = RxStatus.IDLE, message = null, hasMedia = false) }
    }

    fun release() {
        disconnect(quiet = true)
        stopDiscovery()
        main.post { try { player.release() } catch (_: Exception) {} }
        io.shutdown(); writer.shutdown()
    }

    fun setDelayMs(ms: Int) { _ui.update { it.copy(delayMs = ms.coerceIn(-1000, 1000)) } }

    private fun fail(msg: String) {
        _ui.update { it.copy(status = RxStatus.ERROR, message = msg, hasMedia = false) }
        try { socket?.close() } catch (_: Exception) {}
        main.post { try { player.stop() } catch (_: Exception) {} }
    }

    private fun sendRaw(j: JSONObject) {
        val o = out ?: return
        try {
            writer.execute {
                try { o.write((j.toString() + "\n").toByteArray()); o.flush() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // ---- messages --------------------------------------------------------------------------------------

    private fun handle(j: JSONObject) {
        when (j.optString("t")) {
            "pong" -> {
                val c0 = j.optLong("c"); val c1 = SystemClock.elapsedRealtime(); val h = j.optLong("h")
                val rtt = SyncMath.rtt(c0, c1)
                // keep the most precise (lowest round-trip) estimate, let it age so drift is tracked
                if (rtt <= bestRtt + 5L || bestRtt == Long.MAX_VALUE) { offsetMs = SyncMath.clockOffset(c0, c1, h); bestRtt = rtt } else bestRtt += 2L
            }
            "cfg" -> {
                val mode = try { MsMode.valueOf(j.optString("mode")) } catch (e: Exception) { MsMode.MIRROR }
                _ui.update {
                    it.copy(
                        mode = mode,
                        hostW = j.optDouble("hostW", 0.0).toFloat(), hostH = j.optDouble("hostH", 0.0).toFloat()
                    )
                }
                main.post { applyMode(mode) }
            }
            "media" -> {
                val url = j.optString("url")
                val local = j.optBoolean("local", false)
                _ui.update { it.copy(title = j.optString("title"), hasMedia = url.isNotEmpty()) }
                main.post { loadMedia(url, local) }
            }
            "st" -> {
                val pos = j.optLong("pos"); val play = j.optBoolean("play"); val speed = j.optDouble("speed", 1.0).toFloat()
                val hostNow = j.optLong("hostNow"); val ended = j.optBoolean("ended")
                main.post { onHostState(pos, play, speed, hostNow, ended) }
            }
        }
    }

    private fun applyMode(mode: MsMode) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, mode == MsMode.SPEAKER).build()
    }

    private fun loadMedia(url: String, local: Boolean) {
        if (url == lastMediaUrl) return
        lastMediaUrl = url
        if (url.isEmpty()) { player.stop(); player.clearMediaItems(); return }
        val full = if (local) "http://$hostIp:$hostPort$url" else url
        val item = MediaItem.fromUri(Uri.parse(full))
        if (local) {
            player.setMediaSource(ProgressiveMediaSource.Factory(LanDataSource.Factory()).createMediaSource(item))
        } else {
            player.setMediaItem(item)
        }
        applyMode(_ui.value.mode)
        player.playWhenReady = false
        player.prepare()
        lastSeekAt = 0L
    }

    private fun onHostState(pos: Long, play: Boolean, speed: Float, hostNow: Long, ended: Boolean) {
        if (player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE) return
        val now = SystemClock.elapsedRealtime()
        val hostNowEst = now + offsetMs
        val target = (SyncMath.targetPosition(pos, hostNow, hostNowEst, play, speed) + _ui.value.delayMs).coerceAtLeast(0L)
        if (ended) { player.playWhenReady = false; return }
        if (!play) {
            if (player.playWhenReady) player.playWhenReady = false
            if (Math.abs(player.currentPosition - target) > 500L) player.seekTo(target)
            if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed)
            return
        }
        if (!player.playWhenReady) player.playWhenReady = true
        // If buffering, don't trigger seeks which reset the buffer and stall playback
        if (player.playbackState == Player.STATE_BUFFERING) return
        val drift = player.currentPosition - target
        when (val a = SyncMath.decide(drift, speed, now - lastSeekAt > 4000L)) {
            is SyncMath.Action.Seek -> { player.seekTo(target); lastSeekAt = now; player.setPlaybackSpeed(speed) }
            is SyncMath.Action.Speed -> if (player.playbackParameters.speed != a.factor) player.setPlaybackSpeed(a.factor)
            SyncMath.Action.None -> {}
        }
    }

    // ---- discovery -------------------------------------------------------------------------------------

    private var nsd: NsdManager? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun startDiscovery() {
        stopDiscovery()
        try {
            val mgr = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsd = mgr
            _ui.update { it.copy(found = emptyList()) }
            val l = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    synchronized(resolveQueue) { resolveQueue.addLast(serviceInfo) }
                    resolveNext()
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    _ui.update { st -> st.copy(found = st.found.filter { it.name != serviceInfo.serviceName }) }
                }
            }
            discovery = l
            mgr.discoverServices(MsProto.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            // manual address entry still works
        }
    }

    fun stopDiscovery() {
        try { discovery?.let { nsd?.stopServiceDiscovery(it) } } catch (_: Exception) {}
        discovery = null
        synchronized(resolveQueue) { resolveQueue.clear(); resolving = false }
    }

    private fun resolveNext() {
        val mgr = nsd ?: return
        val next: NsdServiceInfo = synchronized(resolveQueue) {
            if (resolving || resolveQueue.isEmpty()) null else { resolving = true; resolveQueue.removeFirst() }
        } ?: return
        try {
            @Suppress("DEPRECATION")
            mgr.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(resolveQueue) { resolving = false }
                    resolveNext()
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val ip = serviceInfo.host?.hostAddress
                    if (ip != null) {
                        val f = FoundHost(serviceInfo.serviceName, ip, serviceInfo.port)
                        _ui.update { st -> st.copy(found = (st.found.filter { it.name != f.name } + f)) }
                    }
                    synchronized(resolveQueue) { resolving = false }
                    resolveNext()
                }
            })
        } catch (e: Exception) {
            synchronized(resolveQueue) { resolving = false }
        }
    }
}
