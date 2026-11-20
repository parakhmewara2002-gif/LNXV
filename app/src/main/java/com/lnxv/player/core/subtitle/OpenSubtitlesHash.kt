package com.lnxv.player.core.subtitle

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The OpenSubtitles "moviehash": file size + the sum of all little-endian 64-bit words in the first and the last
 * 64 KB, modulo 2^64, printed as 16 lowercase hex digits. Files smaller than 128 KB cannot be hashed.
 * Pure logic: bytes are supplied through [readAt] so the caller decides how to read (file, SAF descriptor, test array).
 */
object OpenSubtitlesHash {
    const val CHUNK_SIZE = 65_536
    const val MIN_FILE_SIZE = 2L * CHUNK_SIZE

    /** @return the hash, or null when the file is too small or a read comes back short. */
    fun compute(size: Long, readAt: (offset: Long, length: Int) -> ByteArray): String? {
        if (size < MIN_FILE_SIZE) return null
        var hash = size
        for (offset in longArrayOf(0L, size - CHUNK_SIZE)) {
            val chunk = readAt(offset, CHUNK_SIZE)
            if (chunk.size != CHUNK_SIZE) return null
            val buf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
            while (buf.remaining() >= 8) hash += buf.long // overflow wraps, which is exactly mod 2^64
        }
        return "%016x".format(hash)
    }

    fun compute(bytes: ByteArray): String? = compute(bytes.size.toLong()) { offset, length ->
        bytes.copyOfRange(offset.toInt(), (offset + length).toInt())
    }
}
