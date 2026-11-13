package com.lnxv.player.core.security

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chunked streaming AES-256-GCM with random access.
 *
 * File layout: 16-byte header | chunk 0 | chunk 1 | ...
 * Every chunk is `plaintext(chunkSize) + 16-byte tag` except the last, which may be shorter.
 * Nonce = 4-byte random prefix (from header) + 8-byte big-endian chunk index.
 * AAD = header + chunk index + final flag, so reordering, truncation at a chunk boundary and
 * extension are all detected.
 */
object ChunkedAead {
    const val HEADER_SIZE = 16
    const val TAG_SIZE = 16
    const val DEFAULT_CHUNK_SIZE = 64 * 1024
    private val MAGIC = byteArrayOf('L'.code.toByte(), 'N'.code.toByte(), 'X'.code.toByte(), 'V'.code.toByte())
    private const val VERSION: Byte = 1

    private fun cipher(key: ByteArray, header: ByteArray, index: Long, final: Boolean, mode: Int): Cipher {
        val nonce = ByteBuffer.allocate(12).put(header, 12, 4).putLong(index).array()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val aad = ByteBuffer.allocate(HEADER_SIZE + 9).put(header).putLong(index).put(if (final) 1 else 0).array()
        c.updateAAD(aad)
        return c
    }

    fun newHeader(chunkSize: Int, rnd: SecureRandom = SecureRandom()): ByteArray {
        require(chunkSize in 1024..(16 * 1024 * 1024)) { "bad chunk size" }
        val prefix = ByteArray(4).also { rnd.nextBytes(it) }
        return ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).put(VERSION).put(ByteArray(3))
            .putInt(chunkSize).put(prefix).array()
    }

    private fun chunkSizeOf(header: ByteArray): Int {
        if (header.size != HEADER_SIZE || !header.copyOfRange(0, 4).contentEquals(MAGIC) || header[4] != VERSION) {
            throw IOException("Not an LNXV vault file")
        }
        val cs = ByteBuffer.wrap(header, 8, 4).int
        if (cs !in 1024..(16 * 1024 * 1024)) throw IOException("Corrupt vault header")
        return cs
    }

    fun encryptedSize(plainSize: Long, chunkSize: Int = DEFAULT_CHUNK_SIZE): Long {
        val chunks = if (plainSize == 0L) 1L else (plainSize + chunkSize - 1) / chunkSize
        return HEADER_SIZE + plainSize + chunks * TAG_SIZE
    }

    fun plainSizeOf(encryptedSize: Long, chunkSize: Int): Long {
        val body = encryptedSize - HEADER_SIZE
        if (body < TAG_SIZE) throw IOException("Truncated vault file")
        val full = chunkSize + TAG_SIZE
        val chunks = (body + full - 1) / full
        val plain = body - chunks * TAG_SIZE
        if (plain < 0) throw IOException("Truncated vault file")
        return plain
    }

    /** Encrypting stream. Closing it writes the final chunk; it does NOT close [sink]. */
    class Encryptor(
        private val sink: OutputStream,
        private val key: ByteArray,
        private val chunkSize: Int = DEFAULT_CHUNK_SIZE
    ) : OutputStream() {
        private val header = newHeader(chunkSize)
        private val buf = ByteArray(chunkSize)
        private var filled = 0
        private var index = 0L
        private var closed = false

        init { sink.write(header) }

        override fun write(b: Int) { write(byteArrayOf(b.toByte()), 0, 1) }

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "closed" }
            var o = off
            var remaining = len
            while (remaining > 0) {
                if (filled == chunkSize) flushChunk(false)
                val n = minOf(remaining, chunkSize - filled)
                System.arraycopy(b, o, buf, filled, n)
                filled += n; o += n; remaining -= n
            }
        }

        private fun flushChunk(final: Boolean) {
            val c = cipher(key, header, index, final, Cipher.ENCRYPT_MODE)
            sink.write(c.doFinal(buf, 0, filled))
            index++
            filled = 0
        }

        override fun flush() { sink.flush() }

        override fun close() {
            if (closed) return
            closed = true
            flushChunk(true)
            sink.flush()
            buf.fill(0)
        }
    }

    /** Random-access byte source for ciphertext. */
    interface Source : AutoCloseable {
        fun length(): Long
        /** Reads up to [len] bytes at [pos]; returns bytes read or -1 at EOF. */
        fun readAt(pos: Long, dst: ByteArray, off: Int, len: Int): Int
    }

    class FileSource(path: java.io.File) : Source {
        private val raf = RandomAccessFile(path, "r")
        override fun length(): Long = raf.length()
        @Synchronized
        override fun readAt(pos: Long, dst: ByteArray, off: Int, len: Int): Int {
            raf.seek(pos)
            return raf.read(dst, off, len)
        }
        override fun close() = raf.close()
    }

    class BytesSource(private val data: ByteArray) : Source {
        override fun length(): Long = data.size.toLong()
        override fun readAt(pos: Long, dst: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            val n = minOf(len.toLong(), data.size - pos).toInt()
            System.arraycopy(data, pos.toInt(), dst, off, n)
            return n
        }
        override fun close() {}
    }

    /** Random-access decryptor. Not thread-safe; keeps one decrypted chunk cached. */
    class Reader(private val source: Source, private val key: ByteArray) : AutoCloseable {
        private val header = ByteArray(HEADER_SIZE)
        val chunkSize: Int
        val plainSize: Long
        private val chunkCount: Long
        private var cachedIndex = -1L
        private var cached: ByteArray = ByteArray(0)

        init {
            readFully(0, header, 0, HEADER_SIZE)
            chunkSize = chunkSizeOf(header)
            plainSize = plainSizeOf(source.length(), chunkSize)
            chunkCount = if (plainSize == 0L) 1L else (plainSize + chunkSize - 1) / chunkSize
        }

        private fun readFully(pos: Long, dst: ByteArray, off: Int, len: Int) {
            var done = 0
            while (done < len) {
                val n = source.readAt(pos + done, dst, off + done, len - done)
                if (n < 0) throw EOFException("Truncated vault file")
                done += n
            }
        }

        private fun loadChunk(index: Long) {
            if (index == cachedIndex) return
            val isFinal = index == chunkCount - 1
            val plainLen = if (isFinal) (plainSize - index * chunkSize).toInt() else chunkSize
            val ct = ByteArray(plainLen + TAG_SIZE)
            readFully(HEADER_SIZE + index * (chunkSize + TAG_SIZE), ct, 0, ct.size)
            try {
                cached = cipher(key, header, index, isFinal, Cipher.DECRYPT_MODE).doFinal(ct)
            } catch (e: GeneralSecurityException) {
                throw IOException("Vault data failed authentication", e)
            }
            cachedIndex = index
        }

        /** Reads up to [len] plaintext bytes at [position]; returns -1 at end. */
        fun read(position: Long, dst: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (position >= plainSize) return -1
            var pos = position
            var written = 0
            while (written < len && pos < plainSize) {
                val idx = pos / chunkSize
                loadChunk(idx)
                val inChunk = (pos - idx * chunkSize).toInt()
                val n = minOf(len - written, cached.size - inChunk)
                System.arraycopy(cached, inChunk, dst, off + written, n)
                written += n; pos += n
            }
            return written
        }

        override fun close() {
            cached.fill(0)
            source.close()
        }
    }

    /** Sequential decrypting stream (used for restore). */
    class DecryptingInputStream(private val reader: Reader) : InputStream() {
        private var pos = 0L
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = reader.read(pos, b, off, len)
            if (n > 0) pos += n
            return n
        }
        override fun close() = reader.close()
    }
}
