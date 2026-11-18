package com.lnxv.player.core.subtitle.online

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Keeps subtitles the user downloaded so they load again the next time the same video plays. Files live in the app's
 * private storage, keyed by a hash of the library video id (never by path or title).
 */
class OnlineSubtitleStore(context: Context) {
    private val dir = File(context.filesDir, "online_subs")

    private fun keyFor(videoId: Long): String =
        MessageDigest.getInstance("SHA-256").digest("video:$videoId".toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    fun save(videoId: Long, srt: ByteArray): File {
        dir.mkdirs()
        val f = File(dir, keyFor(videoId) + ".srt")
        f.writeBytes(srt)
        return f
    }

    fun find(videoId: Long): File? = File(dir, keyFor(videoId) + ".srt").takeIf { it.isFile && it.length() > 0 }

    fun forget(videoId: Long) { File(dir, keyFor(videoId) + ".srt").delete() }

    fun clearAll() { dir.listFiles()?.forEach { it.delete() } }
}
