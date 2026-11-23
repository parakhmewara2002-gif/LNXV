package com.lnxv.player.core.youtube

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
 * Native YouTube extractor and search client using official lightweight Innertube payloads.
 * Supports direct video resolution (progressive 720p/360p or adaptive 1080p), and instant search.
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

    suspend fun resolve(rawUrlOrId: String): Result<VideoStream> = withContext(Dispatchers.IO) {
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
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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

            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("YouTube service returned HTTP ${conn.responseCode}"))
            }

            val responseStr = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            val json = JSONObject(responseStr)

            val playability = json.optJSONObject("playabilityStatus")
            val status = playability?.optString("status")
            if (status != "OK") {
                val reason = playability?.optString("reason") ?: "Video unavailable"
                return@withContext Result.failure(Exception("YouTube: $reason"))
            }

            val videoDetails = json.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title") ?: "YouTube Video"
            val author = videoDetails?.optString("author") ?: "YouTube"
            val durationSec = videoDetails?.optLong("lengthSeconds", 0L) ?: 0L
            val thumbnails = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url")

            val streamingData = json.optJSONObject("streamingData") ?: return@withContext Result.failure(
                Exception("No playable streams found")
            )

            // 1. Check progressive formats (audio + video combined)
            val formats = streamingData.optJSONArray("formats")
            var bestProgressiveUrl: String? = null
            var progWidth = 1280
            var progHeight = 720
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.optJSONObject(i) ?: continue
                    val directUrl = fmt.optString("url")
                    if (directUrl.isNotEmpty()) {
                        bestProgressiveUrl = directUrl
                        progWidth = fmt.optInt("width", 1280)
                        progHeight = fmt.optInt("height", 720)
                        break
                    }
                }
            }

            if (bestProgressiveUrl != null) {
                return@withContext Result.success(
                    VideoStream(
                        videoUrl = bestProgressiveUrl,
                        audioUrl = null,
                        title = title,
                        author = author,
                        durationMs = durationSec * 1000L,
                        width = progWidth,
                        height = progHeight,
                        thumbnailUrl = thumbUrl
                    )
                )
            }

            // 2. Check adaptive formats
            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            var bestVideoUrl: String? = null
            var bestAudioUrl: String? = null
            var bestHeight = 0
            var bestWidth = 0

            if (adaptive != null) {
                for (i in 0 until adaptive.length()) {
                    val fmt = adaptive.optJSONObject(i) ?: continue
                    val directUrl = fmt.optString("url")
                    if (directUrl.isEmpty()) continue
                    val mime = fmt.optString("mimeType")

                    if (mime.startsWith("video/")) {
                        val h = fmt.optInt("height", 0)
                        if (h > bestHeight) {
                            bestHeight = h
                            bestWidth = fmt.optInt("width", 0)
                            bestVideoUrl = directUrl
                        }
                    } else if (mime.startsWith("audio/") && bestAudioUrl == null) {
                        bestAudioUrl = directUrl
                    }
                }
            }

            if (bestVideoUrl != null) {
                return@withContext Result.success(
                    VideoStream(
                        videoUrl = bestVideoUrl,
                        audioUrl = bestAudioUrl,
                        title = title,
                        author = author,
                        durationMs = durationSec * 1000L,
                        width = if (bestWidth > 0) bestWidth else 1920,
                        height = if (bestHeight > 0) bestHeight else 1080,
                        thumbnailUrl = thumbUrl
                    )
                )
            }

            Result.failure(Exception("Could not extract stream URL"))
        } catch (e: Exception) {
            Result.failure(e)
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
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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
