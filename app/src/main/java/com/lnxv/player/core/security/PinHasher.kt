package com.lnxv.player.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN hashing with a random per-install salt and PBKDF2WithHmacSHA256.
 * Verification uses a constant-time comparison.
 */
object PinHasher {
    const val SALT_BYTES = 16
    const val HASH_BYTES = 32
    const val DEFAULT_ITERATIONS = 210_000
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8
    const val RECOMMENDED_PIN_LENGTH = 6

    private val random = SecureRandom()

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    fun hash(pin: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(iterations > 0) { "iterations must be positive" }
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: ByteArray, expected: ByteArray, iterations: Int): Boolean {
        if (salt.isEmpty() || expected.isEmpty()) return false
        val actual = hash(pin, salt, iterations)
        return MessageDigest.isEqual(actual, expected)
    }

    /** Legacy SHA-256 scheme with a static salt; only used to migrate old installs. */
    fun legacyHash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("LNXV_SALT_$pin".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifyLegacy(pin: String, storedHex: String): Boolean {
        if (storedHex.isEmpty()) return false
        return MessageDigest.isEqual(
            legacyHash(pin).toByteArray(Charsets.US_ASCII),
            storedHex.toByteArray(Charsets.US_ASCII)
        )
    }
}
