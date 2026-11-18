package com.lnxv.player.core.subtitle

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Heuristic subtitle text decoder (no third-party detector, so no extra licence or APK size).
 *
 * Order: BOM, UTF-16 without BOM (zero byte pattern), strict UTF-8, then legacy code pages chosen by script statistics.
 * A heuristic cannot be perfect (GBK vs Big5, Latin code pages), which is why the player offers a manual override
 * from [CHOICES].
 */
object SubtitleEncoding {
    /** Names shown in the manual override list. "AUTO" means detect. */
    val CHOICES: List<String> = listOf(
        "AUTO", "UTF-8", "UTF-16LE", "UTF-16BE", "windows-1252", "windows-1250", "windows-1251", "windows-1253",
        "windows-1254", "windows-1255", "windows-1256", "windows-1257", "ISO-8859-2", "Shift_JIS", "GBK", "Big5", "EUC-KR"
    )

    fun charsetFor(name: String): Charset? =
        if (name == "AUTO") null else runCatching { Charset.forName(name) }.getOrNull()

    fun decode(bytes: ByteArray, override: Charset? = null): String {
        val cs = override ?: detect(bytes)
        var start = 0
        // Drop the BOM that belongs to the detected encoding.
        if (cs.name() == "UTF-8" && bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) start = 3
        if (cs.name().startsWith("UTF-16") && bytes.size >= 2 && isUtf16Bom(bytes)) start = 2
        return String(bytes, start, bytes.size - start, cs)
    }

    private fun isUtf16Bom(b: ByteArray) =
        (b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) || (b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte())

    fun detect(bytes: ByteArray): Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return Charsets.UTF_8
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return Charsets.UTF_16LE
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return Charsets.UTF_16BE
        guessUtf16WithoutBom(bytes)?.let { return it }
        if (isValid(bytes, Charsets.UTF_8)) return Charsets.UTF_8

        val high = bytes.count { it.toInt() and 0x80 != 0 }
        val nonSpace = bytes.count { it.toInt() !in intArrayOf(0x20, 0x0A, 0x0D, 0x09) }.coerceAtLeast(1)

        // Multi-byte Asian code pages first: strict decoding rejects most random single-byte text.
        asianCharset(bytes)?.let { return it }

        if (high == 0) return Charsets.UTF_8 // pure ASCII
        val highRatio = high.toDouble() / nonSpace
        return if (highRatio < 0.25) {
            Charset.forName("windows-1252") // Western European: a few accented letters
        } else {
            singleByteScript(bytes)
        }
    }

    private fun guessUtf16WithoutBom(bytes: ByteArray): Charset? {
        if (bytes.size < 8) return null
        var evenZero = 0
        var oddZero = 0
        val n = minOf(bytes.size, 4096) / 2 * 2
        var i = 0
        while (i < n) {
            if (bytes[i].toInt() == 0) evenZero++
            if (bytes[i + 1].toInt() == 0) oddZero++
            i += 2
        }
        val pairs = n / 2
        return when {
            oddZero > pairs * 0.3 && evenZero < pairs * 0.05 -> Charsets.UTF_16LE
            evenZero > pairs * 0.3 && oddZero < pairs * 0.05 -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun isValid(bytes: ByteArray, cs: Charset): Boolean = strictDecode(bytes, cs) != null

    private fun strictDecode(bytes: ByteArray, cs: Charset): String? = try {
        cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        null
    }

    private fun asianCharset(bytes: ByteArray): Charset? {
        fun nonAscii(s: String) = s.filter { it.code > 0x7F }
        val ko = strictDecode(bytes, Charset.forName("EUC-KR"))?.let(::nonAscii)
        if (ko != null && ko.isNotEmpty() && ko.count { it in '가'..'힣' } >= ko.length * 0.9) return Charset.forName("EUC-KR")
        val ja = strictDecode(bytes, Charset.forName("Shift_JIS"))?.let(::nonAscii)
        if (ja != null && ja.isNotEmpty() && ja.count { it in '぀'..'ヿ' || it in '｡'..'ﾟ' } >= ja.length * 0.25) {
            return Charset.forName("Shift_JIS")
        }
        val zh = strictDecode(bytes, Charset.forName("GBK"))?.let(::nonAscii)
        if (zh != null && zh.isNotEmpty() && zh.count { it in '一'..'鿿' } >= zh.length * 0.8) return Charset.forName("GBK")
        val big5 = strictDecode(bytes, Charset.forName("Big5"))?.let(::nonAscii)
        if (big5 != null && big5.isNotEmpty() && big5.count { it in '一'..'鿿' } >= big5.length * 0.8) return Charset.forName("Big5")
        return null
    }

    /** Chooses between Cyrillic, Arabic, Greek and Hebrew code pages by counting which one yields real letters. */
    private fun singleByteScript(bytes: ByteArray): Charset {
        val candidates = listOf(
            "windows-1251" to { c: Char -> c in 'Ѐ'..'ӿ' },
            "windows-1256" to { c: Char -> c in '؀'..'ۿ' },
            "windows-1253" to { c: Char -> c in 'Ͱ'..'Ͽ' },
            "windows-1255" to { c: Char -> c in '֐'..'׿' }
        )
        var best = "windows-1252"
        var bestScore = 0
        for ((name, inScript) in candidates) {
            val text = String(bytes, Charset.forName(name))
            val score = text.count(inScript)
            if (score > bestScore) { best = name; bestScore = score }
        }
        return Charset.forName(best)
    }
}
