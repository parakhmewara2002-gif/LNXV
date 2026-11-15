package com.lnxv.player.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Wraps/unwraps raw key bytes. Abstracted so the manager can be unit-tested without a Keystore. */
interface KeyWrapper {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
}

/** AES-GCM wrapper backed by an Android Keystore key (StrongBox when available). */
class KeystoreKeyWrapper(private val alias: String = "lnxv_vault_wrap_v1") : KeyWrapper {

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return generate(strongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        try {
            val b = KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) b.setIsStrongBoxBacked(true)
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(b.build()) }.generateKey()
        } catch (e: Exception) {
            // StrongBox unavailable (StrongBoxUnavailableException) -> fall back to TEE-backed key.
            if (strongBox) return generate(strongBox = false)
            throw e
        }
    }

    override fun wrap(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, wrapped, 0, 12))
        return c.doFinal(wrapped, 12, wrapped.size - 12)
    }

    fun deleteKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }
}

/**
 * Holds the 32-byte vault master key. The wrapped key lives in [keyFile]; the plain key exists only
 * in memory while the vault session is unlocked and is zeroed on [lock].
 */
class VaultKeyManager(private val keyFile: File, private val wrapper: KeyWrapper) {

    @Volatile private var sessionKey: ByteArray? = null

    val isUnlocked: Boolean get() = sessionKey != null

    fun hasKey(): Boolean = keyFile.exists() && keyFile.length() > 0

    /** Creates a new master key if none exists. Returns true if created. */
    @Synchronized
    fun ensureKey(): Boolean {
        if (hasKey()) return false
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val tmp = File(keyFile.parentFile, keyFile.name + ".tmp")
            tmp.writeBytes(wrapper.wrap(raw))
            if (!tmp.renameTo(keyFile)) throw java.io.IOException("Could not store vault key")
        } finally {
            raw.fill(0)
        }
        return true
    }

    @Synchronized
    fun unlock() {
        if (sessionKey != null) return
        sessionKey = wrapper.unwrap(keyFile.readBytes())
    }

    /** Copy of the session key; callers must not keep it. Throws if locked. */
    fun requireKey(): ByteArray = sessionKey?.copyOf() ?: throw java.io.IOException("Vault is locked")

    @Synchronized
    fun lock() {
        sessionKey?.fill(0)
        sessionKey = null
    }

    /** Irreversibly destroys the key (forgot PIN / wipe). Encrypted files become unreadable. */
    @Synchronized
    fun destroy() {
        lock()
        keyFile.delete()
        (wrapper as? KeystoreKeyWrapper)?.runCatching { deleteKey() }
    }
}
