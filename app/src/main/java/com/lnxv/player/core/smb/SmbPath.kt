package com.lnxv.player.core.smb

/** A parsed `smb://host[:port]/share/folder/file` address. [path] has no leading slash and uses `/`. Pure, unit tested. */
data class SmbPath(val host: String, val port: Int, val share: String, val path: String) {
    val isShareRoot: Boolean get() = path.isEmpty()

    fun child(name: String): SmbPath {
        require(isValidSegment(name)) { "Invalid SMB path segment" }
        return copy(path = if (path.isEmpty()) name else "$path/$name")
    }

    fun safeChild(name: String): SmbPath? =
        if (isValidSegment(name)) copy(path = if (path.isEmpty()) name else "$path/$name") else null

    fun parent(): SmbPath? {
        if (path.isEmpty()) return null
        return copy(path = path.substringBeforeLast('/', ""))
    }

    /** `smb://host[:port]/share/a/b` with each segment percent-encoded. */
    fun toUrl(): String {
        val hostPort = if (port == DEFAULT_PORT) host else "$host:$port"
        val segs = (listOf(share) + path.split('/').filter { it.isNotEmpty() }).joinToString("/") { encode(it) }
        return "smb://$hostPort/$segs"
    }

    companion object {
        const val DEFAULT_PORT = 445

        fun isValidSegment(value: String): Boolean =
            value.isNotEmpty() && value != "." && value != ".." &&
                !value.contains('/') && !value.contains('\\') &&
                value.none { it.code < 0x20 || it.code == 0x7f } &&
                value.toByteArray(Charsets.UTF_8).size <= 255

        /** Null when the text is not a usable smb:// address with at least a host and a share, or contains `..`. */
        fun parse(raw: String): SmbPath? {
            val t = raw.trim()
            if (t.length > 8192 || !t.startsWith("smb://", ignoreCase = true)) return null
            val rest = t.substring(6)
            val segs = rest.split('/').filter { it.isNotEmpty() }
            if (segs.size < 2) return null // need host and share
            val hostPort = segs[0]
            if (hostPort.contains('@')) return null // credentials go in the form, never in the URL
            val host = hostPort.substringBefore(':')
            val port = if (hostPort.contains(':')) hostPort.substringAfter(':').toIntOrNull() ?: return null else DEFAULT_PORT
            if (host.isEmpty() || host.any { !(it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') } || port !in 1..65535) return null
            val decoded = segs.drop(1).map { decode(it) ?: return null }
            // Reject decoded separators too: %2f must not smuggle a second path segment.
            if (decoded.any { !isValidSegment(it) }) return null
            return SmbPath(host, port, decoded[0], decoded.drop(1).joinToString("/"))
        }

        fun encode(s: String): String {
            val sb = StringBuilder()
            for (b in s.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xFF
                val unreserved = (c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code) || c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
                if (unreserved) sb.append(c.toChar()) else sb.append('%').append("%02X".format(c))
            }
            return sb.toString()
        }

        fun decode(s: String): String? {
            val out = java.io.ByteArrayOutputStream()
            var i = 0
            val bytes = s.toByteArray(Charsets.UTF_8)
            while (i < bytes.size) {
                val c = bytes[i].toInt()
                if (c == '%'.code) {
                    if (i + 2 > bytes.size - 1) return null
                    val hex = String(bytes, i + 1, 2, Charsets.US_ASCII)
                    val v = hex.toIntOrNull(16) ?: return null
                    out.write(v)
                    i += 3
                } else { out.write(c); i++ }
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        }
    }
}

data class SmbEntry(val name: String, val isDir: Boolean, val sizeBytes: Long)

sealed class SmbResult {
    data class Ok(val entries: List<SmbEntry>) : SmbResult()
    data class Error(val message: String) : SmbResult()
}

/** Session-only SMB credentials for one host. Never written to disk. */
object SmbSession {
    data class Creds(val host: String, val port: Int, val user: String, val password: String, val domain: String)

    @Volatile var creds: Creds? = null

    fun start(p: SmbPath, user: String, password: String, domain: String) { creds = Creds(p.host.lowercase(), p.port, user, password, domain) }
    fun clear() { creds = null }

    /** Credentials for [p] only when it is the host and port the user signed in to. */
    fun forPath(p: SmbPath): Creds? = creds?.takeIf { it.host == p.host.lowercase() && it.port == p.port }
}

/** Implemented by the sideload build only (it is the only one that ships the SMB library). */
interface SmbBackend {
    fun list(path: SmbPath, creds: SmbSession.Creds): SmbResult
    fun newDataSource(): androidx.media3.datasource.DataSource
}
