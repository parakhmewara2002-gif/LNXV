package com.lnxv.player.core.security

import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultKeyManagerTest {
    /** XOR "wrapper" good enough to test the manager's lifecycle without a Keystore. */
    private class FakeWrapper : KeyWrapper {
        override fun wrap(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x5a).toByte() }
        override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
    }

    private fun newManager(): Pair<VaultKeyManager, File> {
        val dir = File.createTempFile("lnxvkey", "").apply { delete(); mkdirs() }
        val f = File(dir, "key.bin")
        return VaultKeyManager(f, FakeWrapper()) to f
    }

    @Test fun lifecycle() {
        val (m, f) = newManager()
        assertFalse(m.hasKey())
        assertTrue(m.ensureKey())
        assertFalse(m.ensureKey()) // second call keeps the same key
        assertTrue(f.length() > 0)
        assertFalse(m.isUnlocked)
        m.unlock()
        assertTrue(m.isUnlocked)
        val k1 = m.requireKey()
        assertTrue(k1.size == 32)
        m.lock()
        assertFalse(m.isUnlocked)
        try { m.requireKey(); fail() } catch (_: IOException) {}
        m.unlock()
        assertArrayEquals(k1, m.requireKey())
    }

    @Test fun destroyRemovesKey() {
        val (m, f) = newManager()
        m.ensureKey(); m.unlock()
        m.destroy()
        assertFalse(f.exists())
        assertFalse(m.isUnlocked)
    }
}
