package com.lnxv.player.core.util

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Fast content fingerprint for duplicate confirmation (Rule D): SHA-256 over file length + 64 KiB from the
 * start, middle and end. Reads at most 192 KiB per file regardless of size, so it is cheap on low-end storage.
 * A match is strong evidence of identical files, not proof; the UI only ever suggests, never auto-deletes.
 */
object PartialHash {
    private const val CHUNK = 64 * 1024

    fun compute(file: File): String? = tryCompute(file).getOrNull()

    fun tryCompute(file: File): Result<String> = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            val md = MessageDigest.getInstance("SHA-256")
            md.update(len.toString().toByteArray())
            val buf = ByteArray(CHUNK)
            val offsets = if (len <= CHUNK * 3L) longArrayOf(0L) else longArrayOf(0L, (len - CHUNK) / 2, len - CHUNK)
            for (off in offsets) {
                raf.seek(off)
                val want = if (len <= CHUNK * 3L) minOf(len, CHUNK * 3L).toInt() else CHUNK
                var left = want
                while (left > 0) {
                    val n = raf.read(buf, 0, minOf(buf.size, left))
                    if (n <= 0) break
                    md.update(buf, 0, n); left -= n
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
