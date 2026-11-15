package com.lnxv.player.core.update

import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

/** Pure (JVM-testable) helpers for update integrity. */
object UpdateVerifier {
    private val ALLOWED_HOSTS = setOf("github.com", "objects.githubusercontent.com", "api.github.com")

    /** Only HTTPS URLs on github.com / objects.githubusercontent.com are ever followed. */
    fun isTrustedUrl(url: String): Boolean = try {
        val u = URI(url)
        u.scheme.equals("https", ignoreCase = true) && u.userInfo == null &&
            (u.port == -1 || u.port == 443) && u.host?.lowercase() in ALLOWED_HOSTS
    } catch (e: Exception) { false }

    /** Accepts `<64 hex>` or `<64 hex>  filename` (sha256sum output). Returns lowercase hex or null. */
    fun parseChecksum(text: String): String? {
        val token = text.trim().lineSequence().firstOrNull()?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: return null
        return token.lowercase().takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    fun sha256Hex(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun fileMatches(file: File, expectedHex: String): Boolean {
        val actual = file.inputStream().use { sha256Hex(it) }
        return MessageDigest.isEqual(actual.toByteArray(Charsets.US_ASCII), expectedHex.lowercase().toByteArray(Charsets.US_ASCII))
    }

    /** Same signer set (compared as SHA-256 digests of the certificates). */
    fun sameSigners(installed: Set<String>, candidate: Set<String>): Boolean =
        installed.isNotEmpty() && installed == candidate

    fun shouldOffer(build: Int, currentBuild: Int, skippedBuild: Int): Boolean =
        build > currentBuild && build != skippedBuild
}
