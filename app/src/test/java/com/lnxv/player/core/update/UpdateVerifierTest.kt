package com.lnxv.player.core.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVerifierTest {
    @Test fun trustedUrls() {
        assertTrue(UpdateVerifier.isTrustedUrl("https://github.com/o/r/releases/download/v1/LNXV.apk"))
        assertTrue(UpdateVerifier.isTrustedUrl("https://objects.githubusercontent.com/abc?x=1"))
    }

    @Test fun untrustedUrls() {
        assertFalse(UpdateVerifier.isTrustedUrl("http://github.com/x.apk"))
        assertFalse(UpdateVerifier.isTrustedUrl("https://evil.com/github.com/x.apk"))
        assertFalse(UpdateVerifier.isTrustedUrl("https://github.com.evil.com/x.apk"))
        assertFalse(UpdateVerifier.isTrustedUrl("https://user@github.com/x.apk"))
        assertFalse(UpdateVerifier.isTrustedUrl("https://github.com:8443/x.apk"))
        assertFalse(UpdateVerifier.isTrustedUrl("ftp://github.com/x"))
        assertFalse(UpdateVerifier.isTrustedUrl("not a url"))
    }

    @Test fun parseChecksum() {
        val h = "a".repeat(64)
        assertEquals(h, UpdateVerifier.parseChecksum("$h  LNXV.apk\n"))
        assertEquals(h, UpdateVerifier.parseChecksum(h.uppercase()))
        assertNull(UpdateVerifier.parseChecksum("short"))
        assertNull(UpdateVerifier.parseChecksum("z".repeat(64)))
        assertNull(UpdateVerifier.parseChecksum(""))
    }

    @Test fun fileChecksumMatchAndMismatch() {
        val f = File.createTempFile("lnxv", ".apk")
        try {
            f.writeBytes("hello".toByteArray())
            val sha = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
            assertTrue(UpdateVerifier.fileMatches(f, sha))
            assertTrue(UpdateVerifier.fileMatches(f, sha.uppercase()))
            assertFalse(UpdateVerifier.fileMatches(f, "0".repeat(64)))
        } finally { f.delete() }
    }

    @Test fun signerComparison() {
        assertTrue(UpdateVerifier.sameSigners(setOf("a"), setOf("a")))
        assertFalse(UpdateVerifier.sameSigners(setOf("a"), setOf("b")))
        assertFalse(UpdateVerifier.sameSigners(emptySet(), emptySet()))
    }

    @Test fun skipLogic() {
        assertTrue(UpdateVerifier.shouldOffer(10, 9, 0))
        assertFalse(UpdateVerifier.shouldOffer(10, 9, 10))
        assertFalse(UpdateVerifier.shouldOffer(9, 9, 0))
        assertTrue(UpdateVerifier.shouldOffer(11, 9, 10))
    }
}
