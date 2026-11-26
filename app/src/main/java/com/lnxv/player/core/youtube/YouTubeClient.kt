package com.lnxv.player.core.youtube

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Native YouTube extractor and search client with Hybrid Browser Session Bridge.
 * Automatically injects legitimate browser cookies and origin tokens to prevent
 * "Sign in to confirm you're not a robot" bot detection while retaining 100% full
 * LNXV native player capabilities (hardware codecs, gestures, EQ, volume boost, PiP).
 */
object YouTubeClient {

    data class VideoStream(
        val videoUrl: String,
        val audioUrl: String?,
        val title: String,
        val author: String,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val thumbnailUrl: String?
    )

    data class SearchItem(
        val videoId: String,
        val title: String,
        val channel: String,
        val duration: String,
        val thumbnailUrl: String?
    )

    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val YOUTUBE_PATTERN = Pattern.compile(
        "^https?://(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/|v/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
    )

    fun isYouTubeUrl(url: String): Boolean {
        val trimmed = url.trim()
        return YOUTUBE_PATTERN.matcher(trimmed).find()
    }

    fun extractVideoId(url: String): String? {
        val matcher = YOUTUBE_PATTERN.matcher(url.trim())
        return if (matcher.find()) matcher.group(1) else null
    }

    /** Retrieves system CookieManager cookies for youtube.com to bypass bot detection. */
    private fun getBrowserCookies(): String? {
        return try {
            val cm = CookieManager.getInstance()
            cm.getCookie("https://www.youtube.com") ?: cm.getCookie("https://m.youtube.com")
        } catch (_: Exception) {
            null
        }
    }

    private data class InnertubeClient(
        val name: String,
        val version: String,
        val headerId: String,
        val deviceModel: String? = null,
        val androidSdk: Int? = null
    )

    /**
     * The client in the JSON body and the X-YouTube-Client-* headers must be the same client. The previous code sent
     * an ANDROID_VR body with WEB_EMBEDDED_PLAYER headers; YouTube increasingly answered UNPLAYABLE for that mix, the
     * old fallback loaded the embed page in a WebView, and embed-disabled videos failed there with "error 152-4".
     * Several clients are tried in order; ANDROID_VR is first because it still returns direct googlevideo URLs.
     */
    private val CLIENTS = listOf(
        InnertubeClient("ANDROID_VR", "1.62.27", "28", "Quest 3", 32),
        InnertubeClient("IOS", "20.10.4", "5"),
        InnertubeClient("ANDROID", "19.44.38", "3", "Pixel 7", 34)
    )

    private fun userAgentFor(c: InnertubeClient): String = when (c.name) {
        "ANDROID_VR" ->
            "com.google.android.apps.youtube.vr.oculus/${c.version} (Linux; U; Android 12; en_US; Quest 3 Build/SQ3A.220605.009.A1) gzip"
        "IOS" -> "com.google.ios.youtube/${c.version} (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
        "ANDROID" -> "com.google.android.youtube/${c.version} (Linux; U; Android 14; en_US) gzip"
        else -> BROWSER_UA
    }

    private fun requestPlayer(videoId: String, client: InnertubeClient): JSONObject? {
        val conn = (URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", userAgentFor(client))
            setRequestProperty("X-YouTube-Client-Name", client.headerId)
            setRequestProperty("X-YouTube-Client-Version", client.version)
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            getBrowserCookies()?.let { setRequestProperty("Cookie", it) }
        }

        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("hl", "en")
                    put("gl", "US")
                    put("clientName", client.name)
                    put("clientVersion", client.version)
                    client.deviceModel?.let { put("deviceModel", it) }
                    client.androidSdk?.let {
                        put("androidSdkVersion", it)
                        put("osName", "Android")
                        put("osVersion", "12")
                    }
                })
            })
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
        if (conn.responseCode != 200) return null
        val text = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
        return if (text.isBlank()) null else JSONObject(text)
    }

    /** Only direct googlevideo URLs are usable: signatureCipher needs a JS decipherer, and anything else is HTML. */
    private fun directUrl(format: JSONObject): String? {
        val url = format.optString("url")
        if (url.isBlank()) return null
        val host = android.net.Uri.parse(url).host ?: return null
        if (!host.endsWith("googlevideo.com")) return null
        return url
    }

    private fun streamFrom(json: JSONObject): VideoStream? {
        if (json.optJSONObject("playabilityStatus")?.optString("status") != "OK") return null
        val streaming = json.optJSONObject("streamingData") ?: return null
        val details = json.optJSONObject("videoDetails")
        val title = details?.optString("title")?.takeIf { it.isNotBlank() } ?: "YouTube Video"
        val author = details?.optString("author")?.takeIf { it.isNotBlank() } ?: "YouTube"
        val durationMs = (details?.optString("lengthSeconds")?.toLongOrNull() ?: 0L) * 1000L
        val thumbs = details?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")

        // 1. Adaptive: best H.264 video + AAC audio, merged by the player. muxed formats are always 360p, so
        // adaptive is preferred whenever both halves exist; the 1080p cap keeps decoding within low-end hardware.
        var videoUrl: String? = null
        var videoW = 0
        var videoH = 0
        var videoScore = -1L
        var audioUrl: String? = null
        var audioScore = -1L
        streaming.optJSONArray("adaptiveFormats")?.let { formats ->
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val mime = f.optString("mimeType")
                val url = directUrl(f) ?: continue
                val bitrate = f.optInt("bitrate", 0)
                if (mime.startsWith("video/")) {
                    val h = f.optInt("height", 0)
                    if (h <= 0 || h > 1080) continue
                    // avc1 = hardware H.264 on every device; VP9/AV1 adaptive is often software-decoded (jank + heat).
                    // Prefer <=720p (native on most phones, far less bandwidth) before falling back to 1080p.
                    val codecBonus = if (mime.contains("avc1")) 10_000_000_000L else 0L
                    val sizeBonus = if (h <= 720) 1_000_000_000L else 0L
                    val score = codecBonus + sizeBonus + h.toLong() * 1_000_000L + bitrate
                    if (score > videoScore) {
                        videoScore = score
                        videoUrl = url
                        videoW = f.optInt("width", 0)
                        videoH = h
                    }
                } else if (mime.startsWith("audio/")) {
                    // Prefer AAC (hardware decode everywhere) at similar bitrate.
                    val score = bitrate.toLong() + if (mime.contains("mp4a")) 1_000_000L else 0L
                    if (score > audioScore) {
                        audioScore = score
                        audioUrl = url
                    }
                }
            }
        }
        if (videoUrl != null && audioUrl != null) {
            return VideoStream(videoUrl, audioUrl, title, author, durationMs, videoW, videoH, thumb)
        }

        // 2. Muxed stream (video + audio in one file): no merge, but YouTube only serves it at 360p.
        var muxedUrl: String? = null
        var muxedW = 0
        var muxedH = 0
        streaming.optJSONArray("formats")?.let { formats ->
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                if (!f.optString("mimeType").startsWith("video/")) continue
                val url = directUrl(f) ?: continue
                val h = f.optInt("height", 0)
                if (muxedUrl == null || h > muxedH) {
                    muxedUrl = url
                    muxedW = f.optInt("width", 0)
                    muxedH = h
                }
            }
        }
        if (muxedUrl != null) {
            return VideoStream(muxedUrl, null, title, author, durationMs, muxedW, muxedH, thumb)
        }

        // 3. Live streams only expose an HLS manifest.
        val hls = streaming.optString("hlsManifestUrl")
        if (hls.isNotBlank()) {
            return VideoStream(hls, null, title, author, durationMs, 0, 0, thumb)
        }
        return null
    }

    suspend fun resolve(rawUrlOrId: String, context: android.content.Context? = null): Result<VideoStream> =
        withContext(Dispatchers.IO) {
            val videoId = if (rawUrlOrId.length == 11 && !rawUrlOrId.contains("/")) rawUrlOrId
            else extractVideoId(rawUrlOrId)
            if (videoId == null) {
                return@withContext Result.failure(IllegalArgumentException("Invalid YouTube URL or ID: $rawUrlOrId"))
            }

            var reason: String? = null
            for (client in CLIENTS) {
                val json = runCatching { requestPlayer(videoId, client) }.getOrNull() ?: continue
                val playability = json.optJSONObject("playabilityStatus")
                val status = playability?.optString("status")
                if (status != "OK") {
                    reason = playability?.optString("reason")?.takeIf { it.isNotBlank() } ?: status
                    continue
                }
                streamFrom(json)?.let { return@withContext Result.success(it) }
                reason = "No playable stream"
            }
            Result.failure(Exception(reason ?: "Could not extract stream"))
        }

    suspend fun search(query: String): Result<List<SearchItem>> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Result.success(emptyList())

        try {
            val client = CLIENTS.first()
            val url = URL("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", userAgentFor(client))
                setRequestProperty("X-YouTube-Client-Name", client.headerId)
                setRequestProperty("X-YouTube-Client-Version", client.version)
                setRequestProperty("Origin", "https://www.youtube.com")
                getBrowserCookies()?.let { setRequestProperty("Cookie", it) }
            }

            val reqPayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("hl", "en")
                        put("gl", "US")
                        put("clientName", client.name)
                        put("clientVersion", client.version)
                        client.deviceModel?.let { put("deviceModel", it) }
                        client.androidSdk?.let {
                            put("androidSdkVersion", it)
                            put("osName", "Android")
                            put("osVersion", "12")
                        }
                    })
                })
                put("query", q)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(reqPayload.toString()) }

            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("Search failed with HTTP ${conn.responseCode}"))
            }

            val responseStr = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            val json = JSONObject(responseStr)

            val sectionList = json.optJSONObject("contents")?.optJSONObject("sectionListRenderer")
            val firstSection = sectionList?.optJSONArray("contents")?.optJSONObject(0)
            val items = firstSection?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")

            val results = mutableListOf<SearchItem>()
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i)?.optJSONObject("compactVideoRenderer") ?: continue
                    val id = item.optString("videoId")
                    if (id.isEmpty()) continue

                    val titleObj = item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
                    val title = titleObj?.optString("text") ?: "YouTube Video"

                    val byline = item.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)
                    val channel = byline?.optString("text") ?: ""

                    val lengthObj = item.optJSONObject("lengthText")
                    val duration = lengthObj?.optString("simpleText")
                        ?: lengthObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""

                    val thumbArray = item.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val thumbUrl = thumbArray?.optJSONObject(thumbArray.length() - 1)?.optString("url")

                    results.add(
                        SearchItem(
                            videoId = id,
                            title = title,
                            channel = channel,
                            duration = duration,
                            thumbnailUrl = thumbUrl
                        )
                    )
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
