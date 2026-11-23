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

    suspend fun resolve(rawUrlOrId: String, context: android.content.Context? = null): Result<VideoStream> = withContext(Dispatchers.IO) {
        val videoId = if (rawUrlOrId.length == 11 && !rawUrlOrId.contains("/")) rawUrlOrId else extractVideoId(rawUrlOrId)
        if (videoId == null) {
            return@withContext Result.failure(IllegalArgumentException("Invalid YouTube URL or ID: $rawUrlOrId"))
        }

        try {
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("X-YouTube-Client-Name", "56")
                setRequestProperty("X-YouTube-Client-Version", "1.61.48")
                setRequestProperty("Origin", "https://www.youtube.com")
                getBrowserCookies()?.let { setRequestProperty("Cookie", it) }
            }

            val reqPayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("hl", "en")
                        put("gl", "US")
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.61.48")
                        put("deviceModel", "Quest 3")
                    })
                })
                put("videoId", videoId)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(reqPayload.toString()) }

            val responseStr = if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            } else ""

            val json = if (responseStr.isNotEmpty()) JSONObject(responseStr) else JSONObject()
            val playability = json.optJSONObject("playabilityStatus")
            val status = playability?.optString("status")

            // If Innertube returns OK with streamingData, use direct streams
            val streamingData = json.optJSONObject("streamingData")
            if (status == "OK" && streamingData != null) {
                val videoDetails = json.optJSONObject("videoDetails")
                val title = videoDetails?.optString("title") ?: "YouTube Video"
                val author = videoDetails?.optString("author") ?: "YouTube"
                val durationSec = videoDetails?.optLong("lengthSeconds", 0L) ?: 0L
                val thumbnails = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url")

                // 1. Progressive streams
                val formats = streamingData.optJSONArray("formats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val fmt = formats.optJSONObject(i) ?: continue
                        val directUrl = fmt.optString("url")
                        if (directUrl.isNotEmpty()) {
                            return@withContext Result.success(
                                VideoStream(
                                    videoUrl = directUrl,
                                    audioUrl = null,
                                    title = title,
                                    author = author,
                                    durationMs = durationSec * 1000L,
                                    width = fmt.optInt("width", 1280),
                                    height = fmt.optInt("height", 720),
                                    thumbnailUrl = thumbUrl
                                )
                            )
                        }
                    }
                }
            }

            // Fallback: If Innertube was blocked by bot check or has no direct url,
            // use Headless Webview extractor to resolve genuine botguard-verified stream without login!
            if (context != null) {
                val headlessUrl = HeadlessYouTubeExtractor.resolveStream(context, videoId)
                if (headlessUrl != null) {
                    return@withContext Result.success(
                        VideoStream(
                            videoUrl = headlessUrl,
                            audioUrl = null,
                            title = "YouTube Video",
                            author = "YouTube",
                            durationMs = 0L,
                            width = 1280,
                            height = 720,
                            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        )
                    )
                }
            }

            val reason = playability?.optString("reason") ?: "Could not extract stream"
            return@withContext Result.failure(Exception(reason))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun search(query: String): Result<List<SearchItem>> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Result.success(emptyList())

        try {
            val url = URL("https://www.youtube.com/youtubei/v1/search")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("X-YouTube-Client-Name", "56")
                setRequestProperty("X-YouTube-Client-Version", "1.61.48")
                setRequestProperty("Origin", "https://www.youtube.com")
                getBrowserCookies()?.let { setRequestProperty("Cookie", it) }
            }

            val reqPayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("hl", "en")
                        put("gl", "US")
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.61.48")
                        put("deviceModel", "Quest 3")
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
