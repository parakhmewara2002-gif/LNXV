package com.lnxv.player.core.security

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChunkedAeadTest {
    private val key = ByteArray(32) { it.toByte() }
    private val cs = 1024

    private fun encrypt(plain: ByteArray, writeStep: Int = 777): ByteArray {
        val out = ByteArrayOutputStream()
        ChunkedAead.Encryptor(out, key, cs).use { enc ->
            var i = 0
            while (i < plain.size) {
                val n = minOf(writeStep, plain.size - i)
                enc.write(plain, i, n); i += n
            }
        }
        return out.toByteArray()
    }

    private fun reader(ct: ByteArray) = ChunkedAead.Reader(ChunkedAead.BytesSource(ct), key)

    private fun randomBytes(n: Int) = ByteArray(n).also { Random(42).nextBytes(it) }

    @Test fun roundTripVariousSizes() {
        for (size in listOf(0, 1, cs - 1, cs, cs + 1, 3 * cs, 3 * cs + 17, 10_000)) {
            val plain = randomBytes(size)
            val ct = encrypt(plain)
            assertEquals(ChunkedAead.encryptedSize(size.toLong(), cs), ct.size.toLong())
            reader(ct).use { r ->
                assertEquals(size.toLong(), r.plainSize)
                val dst = ByteArray(size)
                var pos = 0
                while (pos < size) {
                    val n = r.read(pos.toLong(), dst, pos, size - pos)
                    assertTrue(n > 0); pos += n
                }
                assertArrayEquals("size=$size", plain, dst)
            }
        }
    }

    @Test fun randomAccessSeek() {
        val plain = randomBytes(20_000)
        val ct = encrypt(plain)
        val rnd = Random(7)
        reader(ct).use { r ->
            repeat(200) {
                val pos = rnd.nextInt(plain.size)
                val len = 1 + rnd.nextInt(3000)
                val dst = ByteArray(len)
                val n = r.read(pos.toLong(), dst, 0, len)
                val expect = minOf(len, plain.size - pos)
                assertEquals(expect, n)
                assertArrayEquals(plain.copyOfRange(pos, pos + expect), dst.copyOf(expect))
            }
            assertEquals(-1, r.read(plain.size.toLong(), ByteArray(4), 0, 4))
        }
    }

    @Test fun ciphertextDoesNotContainPlaintext() {
        val plain = ByteArray(4096) { 'A'.code.toByte() }
        val ct = encrypt(plain)
        assertTrue(String(ct, Charsets.ISO_8859_1).indexOf("AAAAAAAAAAAAAAAA") < 0)
    }

    @Test fun tamperingIsDetected() {
        val ct = encrypt(randomBytes(5000))
        ct[ChunkedAead.HEADER_SIZE + 10] = (ct[ChunkedAead.HEADER_SIZE + 10].toInt() xor 1).toByte()
        reader(ct).use { r ->
            try { r.read(0, ByteArray(10), 0, 10); fail("expected IOException") } catch (_: IOException) {}
        }
    }

    @Test fun truncationAtChunkBoundaryIsDetected() {
        val ct = encrypt(randomBytes(5 * cs))
        val cut = ct.copyOf(ChunkedAead.HEADER_SIZE + 3 * (cs + ChunkedAead.TAG_SIZE))
        reader(cut).use { r ->
            try {
                val dst = ByteArray(3 * cs)
                var pos = 0
                while (pos < dst.size) { val n = r.read(pos.toLong(), dst, pos, dst.size - pos); if (n < 0) break; pos += n }
                fail("expected IOException")
            } catch (_: IOException) {}
        }
    }

    @Test fun wrongKeyFails() {
        val ct = encrypt(randomBytes(2000))
        val bad = ChunkedAead.Reader(ChunkedAead.BytesSource(ct), ByteArray(32) { 9 })
        try { bad.read(0, ByteArray(10), 0, 10); fail() } catch (_: IOException) {}
    }

    @Test fun notAVaultFileRejected() {
        try { reader(ByteArray(100)); fail() } catch (_: IOException) {}
    }

    @Test fun sequentialStreamMatches() {
        val plain = randomBytes(9999)
        val ct = encrypt(plain)
        val out = ChunkedAead.DecryptingInputStream(reader(ct)).use { it.readBytes() }
        assertArrayEquals(plain, out)
    }
}
