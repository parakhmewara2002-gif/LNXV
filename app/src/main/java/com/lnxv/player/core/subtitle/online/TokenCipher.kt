package com.lnxv.player.core.subtitle.online

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the OpenSubtitles session token with an Android Keystore AES-256-GCM key (alias is separate from the Vault's keys).
 * Only the token is stored; the account password is never persisted.
 */
object TokenCipher {
    private const val ALIAS = "lnxv_opensubtitles_token"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val input = plain.toByteArray(Charsets.UTF_8)
        return try {
            val ct = cipher.doFinal(input)
            Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
        } finally {
            input.fill(0)
        }
    }

    /** @return null when the value cannot be decrypted (for example after a Keystore reset); the user then logs in again. */
    fun decrypt(encoded: String): String? = try {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        var plain: ByteArray? = null
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            plain = cipher.doFinal(raw, 12, raw.size - 12)
            String(plain, Charsets.UTF_8)
        } finally {
            raw.fill(0)
            plain?.fill(0)
        }
    } catch (e: Exception) {
        null
    }
}
