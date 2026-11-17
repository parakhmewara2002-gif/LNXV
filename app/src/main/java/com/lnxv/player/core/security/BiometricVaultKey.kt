package com.lnxv.player.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore key that can only be used right after a successful strong biometric (BiometricPrompt.CryptoObject).
 * It wraps a second copy of the vault master key, so biometric unlock needs real hardware-backed authentication
 * instead of a client-side boolean. The key is invalidated when biometrics are enrolled or removed.
 */
object BiometricVaultKey {
    private const val ALIAS = "lnxv_vault_bio_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun ks(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun newKey(): SecretKey {
        runCatching { ks().deleteEntry(ALIAS) }
        val b = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 24) b.setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) {
            b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            b.setUserAuthenticationValidityDurationSeconds(-1)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(b.build()) }.generateKey()
    }

    /** Fresh key + cipher in ENCRYPT mode, to be handed to BiometricPrompt. */
    fun newEncryptCipher(): Cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, newKey()) }

    /** DECRYPT cipher for [iv], or null when no key exists. Throws KeyPermanentlyInvalidatedException if biometrics changed. */
    fun decryptCipher(iv: ByteArray): Cipher? {
        val key = ks().getKey(ALIAS, null) as? SecretKey ?: return null
        return Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
    }

    fun deleteKey() { runCatching { ks().deleteEntry(ALIAS) } }
}
