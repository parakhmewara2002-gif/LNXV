package com.lnxv.player.core.util

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PartialHashTest {
    private fun f(bytes: ByteArray) = File.createTempFile("phash", ".bin").apply { writeBytes(bytes); deleteOnExit() }

    @Test fun noException() { val r = PartialHash.tryCompute(f(ByteArray(10))); assertTrue("AssertionError: ${r.exceptionOrNull()}", r.isSuccess) }

    @Test fun sameContentSameHash() {
        val b = ByteArray(500_000) { (it % 251).toByte() }
        assertEquals(PartialHash.compute(f(b)), PartialHash.compute(f(b)))
    }
    @Test fun differentMiddleDifferentHash() {
        val a = ByteArray(500_000) { 1 }
        val b = a.copyOf().also { it[250_000] = 9 }
        assertNotEquals(PartialHash.compute(f(a)), PartialHash.compute(f(b)))
    }
    @Test fun smallFileHashed() = assertNotNull(PartialHash.compute(f(ByteArray(10) { 3 })))
    @Test fun missingFileNull() = assertNull(PartialHash.compute(File("/no/such/file")))
}
