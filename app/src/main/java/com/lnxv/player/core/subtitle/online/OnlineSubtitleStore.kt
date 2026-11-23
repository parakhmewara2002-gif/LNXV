package com.lnxv.player.core.subtitle.online

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Keeps subtitles the user downloaded so they load again the next time the same video plays. Files live in the app's
 * private storage, keyed by a hash of the library video id (never by path or title).
 */
class OnlineSubtitleStore(context: Context) {
    private val dir = File(context.filesDir, "online_subs")

    private fun keyFor(videoId: Long): String =
        MessageDigest.getInstance("SHA-256").digest("video:$videoId".toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    @Synchronized
    fun save(videoId: Long, srt: ByteArray): File {
        if (srt.isEmpty() || srt.size > MAX_SUBTITLE_BYTES) throw IOException("Subtitle file is empty or too large")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create subtitle storage")
        val file = File(dir, keyFor(videoId) + ".srt")
        val temp = File(dir, file.name + ".tmp")
        try {
            temp.outputStream().use { output ->
                output.write(srt)
                output.flush()
                output.fd.sync()
            }
            if (!temp.renameTo(file)) throw IOException("Could not save subtitle file")
            return file
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    @Synchronized
    fun find(videoId: Long): File? =
        File(dir, keyFor(videoId) + ".srt").takeIf { it.isFile && it.length() in 1..MAX_SUBTITLE_BYTES.toLong() }

    @Synchronized
    fun forget(videoId: Long) {
        val file = File(dir, keyFor(videoId) + ".srt")
        file.delete()
        File(dir, file.name + ".tmp").delete()
    }

    @Synchronized
    fun clearAll() { dir.listFiles()?.forEach { it.delete() } }

    companion object {
        private const val MAX_SUBTITLE_BYTES = 4 * 1024 * 1024
    } }
}
