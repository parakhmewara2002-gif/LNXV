package com.lnxv.player.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin wrapper over BiometricPrompt (BIOMETRIC_STRONG only; no device-credential fallback, the PIN is the fallback).
 * Note: this is an app-level gate, like the PIN; the vault master key is Keystore-wrapped and not bound to the
 * biometric via a CryptoObject. NOT DEVICE-TESTED.
 */
object BiometricGate {
    fun canAuthenticate(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, title: String, negative: String, onSuccess: () -> Unit, onFailure: () -> Unit = {}) {
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure()
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negative)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), cb).authenticate(info)
    }

    /** Prompt bound to a Keystore [cipher]: [onSuccess] receives the cipher that biometric authentication unlocked. */
    fun promptCrypto(
        activity: FragmentActivity,
        cipher: javax.crypto.Cipher,
        title: String,
        negative: String,
        onSuccess: (javax.crypto.Cipher) -> Unit,
        onFailure: () -> Unit = {}
    ) {
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c != null) onSuccess(c) else onFailure()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure()
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negative)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), cb)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
