package com.lnxv.player.core.model

/** Buffer sizing for ExoPlayer's DefaultLoadControl. */
data class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int,
    val targetBytes: Int
)

object BufferProfiles {
    private val NETWORK_SCHEMES = setOf("http", "https", "rtsp")
    private const val MB = 1024 * 1024

    fun isNetwork(scheme: String?): Boolean = scheme != null && scheme.lowercase() in NETWORK_SCHEMES

    /**
     * Local files need little read-ahead (fast seeks, low RAM). Network streams get a deeper buffer so a
     * slow connection does not stall playback. Low-RAM devices get roughly half of each.
     */
    fun forSource(network: Boolean, lowRam: Boolean): BufferProfile = if (network) {
        BufferProfile(
            minBufferMs = if (lowRam) 12_000 else 25_000,
            maxBufferMs = if (lowRam) 30_000 else 60_000,
            playbackBufferMs = 2_500,
            rebufferMs = 5_000,
            targetBytes = if (lowRam) 32 * MB else 64 * MB
        )
    } else {
        BufferProfile(
            minBufferMs = if (lowRam) 8_000 else 15_000,
            maxBufferMs = if (lowRam) 20_000 else 40_000,
            playbackBufferMs = 1_500,
            rebufferMs = 3_000,
            targetBytes = if (lowRam) 16 * MB else 32 * MB
        )
    }
}
