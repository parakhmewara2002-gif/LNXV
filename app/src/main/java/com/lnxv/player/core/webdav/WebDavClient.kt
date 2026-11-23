package com.lnxv.player.core.webdav

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

sealed class WebDavResult {
    data class Ok(val entries: List<WebDavEntry>) : WebDavResult()
    data class Error(val message: String) : WebDavResult()
}

/** Lists one WebDAV folder over HTTPS. Blocking: call from Dispatchers.IO. Redirects are not followed. */
object WebDavClient {
    fun list(url: String, user: String, password: String): WebDavResult {
        val uri = try { URI(url.trim()) } catch (e: Exception) { return WebDavResult.Error("This is not a valid URL") }
        if (uri.scheme?.lowercase() != "https") return WebDavResult.Error("WebDAV needs an https:// address")
        if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null || (uri.port != -1 && uri.port !in 1..65535)) {
            return WebDavResult.Error("Use separate credentials and a clean HTTPS folder address")
        }
        val host = uri.host ?: return WebDavResult.Error("URL has no host")
        val decodedPath = uri.path ?: "/"
        if (decodedPath.split('/').any { it == "." || it == ".." } || decodedPath.contains('\\')) {
            return WebDavResult.Error("The WebDAV folder path is invalid")
        }
        val port = if (uri.port == -1) 443 else uri.port
        val rawPath = uri.rawPath?.ifEmpty { "/" } ?: "/"
        val auth = if (user.isNotEmpty()) WebDavAuth.basicHeader(user, password) else null
        return try {
            val tcp = Socket()
            tcp.connect(InetSocketAddress(host, port), 15_000)
            val socket = try {
                (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(tcp, host, port, true) as SSLSocket
            } catch (e: Exception) {
                tcp.close()
                throw e
            }
            socket.use { s ->
                s.soTimeout = 15_000
                val secureProtocols = s.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }
                if (secureProtocols.isNotEmpty()) s.enabledProtocols = secureProtocols.toTypedArray()
                s.startHandshake()
                // Hostname verification: the default factory does not do it for raw sockets.
                val ok = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(host, s.session)
                if (!ok) return WebDavResult.Error("The server certificate does not match its address")
                s.getOutputStream().apply { write(WebDavHttp.buildPropfind(if (port == 443) host else "$host:$port", rawPath, auth)); flush() }
                val buf = ByteArrayOutputStream()
                val tmp = ByteArray(8192)
                val input = s.getInputStream()
                while (true) {
                    val n = input.read(tmp)
                    if (n < 0) break
                    buf.write(tmp, 0, n)
                    if (buf.size() > WebDavHttp.MAX_BODY) return WebDavResult.Error("The folder listing is too large")
                }
                val resp = WebDavHttp.parseResponse(buf.toByteArray())
                when (resp.status) {
                    207 -> WebDavResult.Ok(WebDavParser.parse(String(resp.body, Charsets.UTF_8), uri.toASCIIString()))
                    401 -> WebDavResult.Error("Wrong username or password")
                    403 -> WebDavResult.Error("Access denied")
                    404 -> WebDavResult.Error("Folder not found")
                    301, 302, 307, 308 -> WebDavResult.Error("The server redirected; use its final address")
                    else -> WebDavResult.Error("Server error (${resp.status})")
                }
            }
        } catch (e: javax.net.ssl.SSLException) {
            WebDavResult.Error("Secure connection failed")
        } catch (e: java.io.IOException) {
            WebDavResult.Error("Could not connect")
        } catch (e: IllegalArgumentException) {
            WebDavResult.Error("The server did not answer like WebDAV")
        }
    }
}
