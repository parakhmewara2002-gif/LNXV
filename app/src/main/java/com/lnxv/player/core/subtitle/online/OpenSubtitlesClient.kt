package com.lnxv.player.core.subtitle.online

import com.lnxv.player.core.subtitle.OpenSubtitlesApi
import com.lnxv.player.core.subtitle.OsDownload
import com.lnxv.player.core.subtitle.OsError
import com.lnxv.player.core.subtitle.OsLogin
import com.lnxv.player.core.subtitle.OsSubtitle
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Minimal OpenSubtitles REST v1 client on HttpURLConnection (no extra HTTP stack). Every call is user-initiated:
 * nothing here runs unless the user tapped Search / Download / Log in. Timeouts bound every request.
 */
class OpenSubtitlesClient(
    private val settings: OnlineSubtitleSettings,
    private val userAgent: String
) {
    suspend fun search(query: OpenSubtitlesApi.SearchQuery): List<OsSubtitle> = withContext(Dispatchers.IO) {
        val cfg = settings.current()
        if (cfg.apiKey.isBlank()) throw OsError.NoApiKey
        val body = http(OpenSubtitlesApi.searchUrl(query, cfg.baseUrl), "GET", null, cfg.apiKey, settings.token(), MAX_JSON)
        ensureActive()
        OpenSubtitlesApi.rank(OpenSubtitlesApi.parseSearch(String(body, Charsets.UTF_8)))
    }

    /** @return the subtitle bytes (SRT) and the download metadata (quota). */
    suspend fun download(fileId: Long): Pair<ByteArray, OsDownload> = withContext(Dispatchers.IO) {
        val cfg = settings.current()
        if (cfg.apiKey.isBlank()) throw OsError.NoApiKey
        val meta = http(OpenSubtitlesApi.downloadUrl(cfg.baseUrl), "POST", OpenSubtitlesApi.downloadBody(fileId), cfg.apiKey, settings.token(), MAX_JSON)
        val info = OpenSubtitlesApi.parseDownload(String(meta, Charsets.UTF_8))
        settings.setQuota(info.remaining)
        if (!OpenSubtitlesApi.isAllowedDownloadLink(info.link)) throw OsError.Disallowed
        ensureActive()
        // The file link is a plain https download; the API key and token are not sent to it.
        val bytes = http(info.link, "GET", null, null, null, MAX_FILE)
        bytes to info
    }

    /** Logs in once and stores only the token. The password is used for this call and then dropped by the caller. */
    suspend fun login(username: String, password: String): OsLogin = withContext(Dispatchers.IO) {
        val cfg = settings.current()
        if (cfg.apiKey.isBlank()) throw OsError.NoApiKey
        val resp = http(OpenSubtitlesApi.loginUrl(cfg.baseUrl), "POST", OpenSubtitlesApi.loginBody(username, password), cfg.apiKey, null, MAX_JSON)
        val login = OpenSubtitlesApi.parseLogin(String(resp, Charsets.UTF_8))
        settings.setUsername(username)
        settings.saveLogin(login.token, login.baseUrl?.let { OpenSubtitlesApi.host(it) })
        login
    }

    private fun http(url: String, method: String, body: String?, apiKey: String?, token: String?, maxBytes: Int): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "*/*")
            if (apiKey != null) {
                conn.setRequestProperty("Api-Key", apiKey)
                conn.setRequestProperty("Accept", "application/json")
            }
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw OsError.fromStatus(code)
            return conn.inputStream.use { readLimited(it, maxBytes) }
        } catch (e: SocketTimeoutException) {
            throw OsError.Timeout
        } catch (e: UnknownHostException) {
            throw OsError.NoNetwork
        } catch (e: ConnectException) {
            throw OsError.NoNetwork
        } catch (e: IOException) {
            throw OsError.NoNetwork
        } finally {
            conn.disconnect()
        }
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw OsError.BadResponse
        }
        return out.toByteArray()
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val MAX_JSON = 2 * 1024 * 1024
        const val MAX_FILE = 4 * 1024 * 1024
    }
}
