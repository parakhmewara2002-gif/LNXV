package com.lnxv.player.multiscreen

import android.net.Uri
import androidx.media3.common.VideoSize
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface

enum class MsMode { SPEAKER, MIRROR, SPAN }

/** Shared wire/config values. Messages are one JSON object per line over TCP; media is plain HTTP on the same port. */
object MsProto {
    const val SERVICE_TYPE = "_lnxv._tcp."
    const val MEDIA_PREFIX = "/m/"
    const val MAX_LINE = 8192
}

/** Display aspect (w/h) of a decoded video, honouring pixel ratio and unapplied rotation. */
fun videoAspect(vs: VideoSize): Float {
    if (vs.width <= 0 || vs.height <= 0) return 16f / 9f
    val rotated = vs.unappliedRotationDegrees % 180 != 0
    val w = vs.width * (if (vs.pixelWidthHeightRatio > 0f) vs.pixelWidthHeightRatio else 1f)
    val h = vs.height.toFloat()
    return if (rotated) h / w else w / h
}

/** Reads a line (up to \n, \r stripped). Null at end of stream. */
internal fun readAsciiLine(input: InputStream): String? {
    val sb = StringBuilder()
    while (true) {
        val b = input.read()
        if (b == -1) return if (sb.isEmpty()) null else sb.toString()
        if (b == '\n'.code) return sb.toString().trimEnd('\r')
        sb.append(b.toChar())
        if (sb.length > MsProto.MAX_LINE) throw IOException("line too long")
    }
}

/** Best guess of this phone's LAN address (Wi-Fi or hotspot first). */
fun localLanIp(): String? {
    return try {
        val ifaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { n ->
                val name = n.name.lowercase()
                if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")) 0 else 1
            }
        ifaces.flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    } catch (e: Exception) {
        null
    }
}

/** True for URIs the host can hand out as a stream. */
internal fun isLocalMedia(uri: Uri): Boolean = uri.scheme == "content" || uri.scheme == "file"
internal fun isNetworkMedia(uri: Uri): Boolean = uri.scheme == "http" || uri.scheme == "https"
