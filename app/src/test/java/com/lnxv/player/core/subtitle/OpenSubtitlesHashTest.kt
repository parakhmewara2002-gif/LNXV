package com.lnxv.player.core.subtitle

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenSubtitlesHashTest {
    @Test fun `all zero file hashes to its own size`() {
        val size = 200_000
        assertEquals("%016x".format(size), OpenSubtitlesHash.compute(ByteArray(size)))
    }

    @Test fun `files under 128 KB have no hash`() {
        assertNull(OpenSubtitlesHash.compute(ByteArray(131_071)))
        assertNull(OpenSubtitlesHash.compute(ByteArray(0)))
        assertNull(OpenSubtitlesHash.compute(ByteArray(65_536)))
    }

    @Test fun `exactly 128 KB is hashed`() {
        assertEquals("%016x".format(131_072), OpenSubtitlesHash.compute(ByteArray(131_072)))
    }

    @Test fun `matches an independent BigInteger implementation on pseudo random data`() {
        val size = 300_001
        val data = ByteArray(size).also { java.util.Random(42).nextBytes(it) }
        val mod = BigInteger.ONE.shiftLeft(64)
        var sum = BigInteger.valueOf(size.toLong())
        fun add(from: Int) {
            val bb = ByteBuffer.wrap(data, from, 65_536).order(ByteOrder.LITTLE_ENDIAN)
            repeat(65_536 / 8) {
                val w = bb.long
                sum = sum.add(BigInteger(java.lang.Long.toUnsignedString(w)))
            }
        }
        add(0); add(size - 65_536)
        assertEquals("%016x".format(sum.mod(mod).toLong()), OpenSubtitlesHash.compute(data))
    }

    @Test fun `wraps around on overflow`() {
        val data = ByteArray(131_072) { 0xFF.toByte() }
        // 2 * 8192 words of 0xFFFF_FFFF_FFFF_FFFF == -16384 (mod 2^64); plus size 131072.
        val expected = 131_072L + (-1L) * 16_384L
        assertEquals("%016x".format(expected), OpenSubtitlesHash.compute(data))
    }

    @Test fun `short read yields null`() {
        assertNull(OpenSubtitlesHash.compute(200_000) { _, _ -> ByteArray(10) })
    }
}
