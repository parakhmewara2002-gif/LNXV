package com.lnxv.player.core.subtitle

import java.net.URLEncoder
import org.json.JSONObject

/** One search result, already reduced to what the UI needs. */
data class OsSubtitle(
    val fileId: Long,
    val fileName: String,
    val release: String,
    val language: String,
    val downloadCount: Int,
    val rating: Double,
    val hashMatch: Boolean,
    val hearingImpaired: Boolean,
    val aiTranslated: Boolean,
    val machineTranslated: Boolean
)

data class OsDownload(val link: String, val fileName: String, val remaining: Int?, val requests: Int?, val resetTimeUtc: String?)

data class OsLogin(val token: String, val baseUrl: String?)

/** What can go wrong, as the UI wants to describe it. */
sealed class OsError(message: String) : Exception(message) {
    object NoApiKey : OsError("No API key")
    object NoNetwork : OsError("No network")
    object Timeout : OsError("Timed out")
    object Unauthorized : OsError("401")
    object Forbidden : OsError("403")
    object QuotaExceeded : OsError("406")
    object RateLimited : OsError("429")
    class Server(val code: Int) : OsError("Server error $code")
    class Other(val code: Int) : OsError("HTTP $code")
    object BadResponse : OsError("Unreadable response")
    object Disallowed : OsError("Download link is not from OpenSubtitles")

    companion object {
        fun fromStatus(code: Int): OsError = when (code) {
            401 -> Unauthorized
            403 -> Forbidden
            406 -> QuotaExceeded
            429 -> RateLimited
            in 500..599 -> Server(code)
            else -> Other(code)
        }
    }
}

/** Pure request building and response parsing for the OpenSubtitles REST API v1. No I/O here. */
object OpenSubtitlesApi {
    const val DEFAULT_HOST = "api.opensubtitles.com"

    data class SearchQuery(
        val movieHash: String? = null,
        val query: String? = null,
        val languages: List<String> = emptyList(),
        val season: Int? = null,
        val episode: Int? = null
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun host(baseUrl: String?): String {
        val raw = baseUrl?.trim().orEmpty()
        if (raw.isEmpty()) return DEFAULT_HOST
        val uri = runCatching { java.net.URI(if (raw.contains("://")) raw else "https://$raw") }.getOrNull()
            ?: return DEFAULT_HOST
        if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1 ||
            uri.rawPath !in listOf(null, "", "/") || uri.rawQuery != null || uri.rawFragment != null
        ) return DEFAULT_HOST
        val candidate = uri.host?.lowercase() ?: return DEFAULT_HOST
        // A dot boundary is required: evilopensubtitles.com is not an OpenSubtitles subdomain.
        return candidate.takeIf { it == "opensubtitles.com" || it.endsWith(".opensubtitles.com") } ?: DEFAULT_HOST
    }

    /** Parameters are lower case and alphabetically ordered, which is what the API documents to avoid redirects. */
    fun searchUrl(q: SearchQuery, baseUrl: String? = null): String {
        val params = sortedMapOf<String, String>()
        q.movieHash?.takeIf { it.isNotBlank() }?.let { params["moviehash"] = it.lowercase(); params["moviehash_match"] = "include" }
        q.query?.trim()?.takeIf { it.isNotEmpty() }?.let { params["query"] = it.lowercase() }
        if (q.languages.isNotEmpty()) params["languages"] = q.languages.map { it.lowercase() }.sorted().joinToString(",")
        q.season?.let { params["season_number"] = it.toString() }
        q.episode?.let { params["episode_number"] = it.toString() }
        return "https://${host(baseUrl)}/api/v1/subtitles?" + params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
    }

    fun downloadUrl(baseUrl: String? = null) = "https://${host(baseUrl)}/api/v1/download"
    fun loginUrl(baseUrl: String? = null) = "https://${host(baseUrl)}/api/v1/login"

    fun downloadBody(fileId: Long): String = JSONObject().put("file_id", fileId).put("sub_format", "srt").toString()
    fun loginBody(username: String, password: String): String = JSONObject().put("username", username).put("password", password).toString()

    fun parseSearch(json: String): List<OsSubtitle> {
        val root = try { JSONObject(json) } catch (e: Exception) { throw OsError.BadResponse }
        val data = root.optJSONArray("data") ?: throw OsError.BadResponse
        val out = ArrayList<OsSubtitle>(data.length())
        for (i in 0 until data.length()) {
            val attrs = data.optJSONObject(i)?.optJSONObject("attributes") ?: continue
            val file = attrs.optJSONArray("files")?.optJSONObject(0) ?: continue
            val fileId = file.optLong("file_id", -1L)
            if (fileId <= 0L) continue
            out += OsSubtitle(
                fileId = fileId,
                fileName = file.optString("file_name", ""),
                release = attrs.optString("release", ""),
                language = attrs.optString("language", ""),
                downloadCount = attrs.optInt("download_count", 0),
                rating = attrs.optDouble("ratings", 0.0).let { if (it.isNaN()) 0.0 else it },
                hashMatch = attrs.optBoolean("moviehash_match", false),
                hearingImpaired = attrs.optBoolean("hearing_impaired", false),
                aiTranslated = attrs.optBoolean("ai_translated", false),
                machineTranslated = attrs.optBoolean("machine_translated", false)
            )
        }
        return out
    }

    /** Hash matches first, then higher rating, then more downloads. Stable for ties. */
    fun rank(results: List<OsSubtitle>): List<OsSubtitle> =
        results.distinctBy { it.fileId }
            .sortedWith(compareByDescending<OsSubtitle> { it.hashMatch }.thenByDescending { it.rating }.thenByDescending { it.downloadCount })

    fun parseDownload(json: String): OsDownload {
        val o = try { JSONObject(json) } catch (e: Exception) { throw OsError.BadResponse }
        val link = o.optString("link", "")
        if (link.isEmpty()) throw OsError.BadResponse
        return OsDownload(
            link = link,
            fileName = o.optString("file_name", "subtitle.srt"),
            remaining = if (o.has("remaining")) o.optInt("remaining") else null,
            requests = if (o.has("requests")) o.optInt("requests") else null,
            resetTimeUtc = o.optString("reset_time_utc", "").ifEmpty { null }
        )
    }

    fun parseLogin(json: String): OsLogin {
        val o = try { JSONObject(json) } catch (e: Exception) { throw OsError.BadResponse }
        val token = o.optString("token", "")
        if (token.isEmpty()) throw OsError.BadResponse
        return OsLogin(token, o.optString("base_url", "").ifEmpty { null })
    }

    /** Download links must be https and point at OpenSubtitles; anything else is refused. */
    fun isAllowedDownloadLink(link: String): Boolean {
        val uri = try { java.net.URI(link) } catch (e: Exception) { return false }
        val h = uri.host?.lowercase() ?: return false
        return uri.scheme == "https" && (h == "opensubtitles.com" || h.endsWith(".opensubtitles.com") || h == "opensubtitles.org" || h.endsWith(".opensubtitles.org"))
    }
}
