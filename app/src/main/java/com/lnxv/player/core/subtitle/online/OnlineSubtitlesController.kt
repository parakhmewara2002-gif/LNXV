package com.lnxv.player.core.subtitle.online

import android.content.Context
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.core.subtitle.OpenSubtitlesApi
import com.lnxv.player.core.subtitle.OpenSubtitlesHash
import com.lnxv.player.core.subtitle.OsError
import com.lnxv.player.core.subtitle.OsSubtitle
import com.lnxv.player.core.subtitle.SubtitleQueryBuilder
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnlineSubtitlesUi(
    val loading: Boolean = false,
    val searched: Boolean = false,
    val results: List<OsSubtitle> = emptyList(),
    val error: OsError? = null,
    val downloadingId: Long? = null,
    val usedHash: Boolean = false
)

/**
 * Drives the "Search online" sheet. Nothing is sent until the user taps Search; the file hash and name are only
 * computed and sent then. Cancelling (closing the sheet) drops the in-flight request.
 */
class OnlineSubtitlesController(
    private val scope: CoroutineScope,
    private val context: Context,
    val settings: OnlineSubtitleSettings,
    private val client: OpenSubtitlesClient,
    private val store: OnlineSubtitleStore
) {
    private val _state = MutableStateFlow(OnlineSubtitlesUi())
    val state: StateFlow<OnlineSubtitlesUi> = _state.asStateFlow()
    val config: StateFlow<OnlineSubtitleConfig> =
        settings.config.stateIn(scope, SharingStarted.Eagerly, OnlineSubtitleConfig())

    private var job: Job? = null
    private var requestGeneration = 0L

    fun defaultQueryFor(video: VideoItem?): String =
        if (video == null || video.isVault) "" else SubtitleQueryBuilder.parse(video.title.ifBlank { File(video.path).name }).query

    fun cancel() {
        requestGeneration++
        job?.cancel()
        job = null
        _state.value = _state.value.copy(loading = false, downloadingId = null)
    }

    fun reset() {
        cancel()
        _state.value = OnlineSubtitlesUi()
    }

    /** Local library files get a hash search first; the text query is used when there is no hash, no match, or [useHash] is false. */
    fun search(video: VideoItem?, text: String, languages: List<String>, useHash: Boolean) {
        val generation = ++requestGeneration
        job?.cancel()
        // Never send a private vault filename or hash to a third-party subtitle service.
        if (video?.isVault == true) {
            _state.value = OnlineSubtitlesUi(searched = true)
            return
        }
        _state.value = OnlineSubtitlesUi(loading = true)
        job = scope.launch {
            try {
                val parsed = SubtitleQueryBuilder.parse(text)
                var results: List<OsSubtitle> = emptyList()
                var usedHash = false
                val hash = if (useHash && video != null) hashOf(video) else null
                if (hash != null) {
                    results = client.search(OpenSubtitlesApi.SearchQuery(movieHash = hash, languages = languages))
                    usedHash = results.isNotEmpty()
                }
                if (results.isEmpty() && text.isNotBlank()) {
                    results = client.search(
                        OpenSubtitlesApi.SearchQuery(query = parsed.query, languages = languages, season = parsed.season, episode = parsed.episode)
                    )
                }
                currentCoroutineContext().ensureActive()
                if (generation == requestGeneration) {
                    _state.value = OnlineSubtitlesUi(searched = true, results = results, usedHash = usedHash)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OsError) {
                if (generation == requestGeneration) _state.value = OnlineSubtitlesUi(searched = true, error = e)
            } catch (e: Exception) {
                if (generation == requestGeneration) _state.value = OnlineSubtitlesUi(searched = true, error = OsError.BadResponse)
            }
        }
    }

    /** Downloads one result and hands the saved file to [onReady]. Library videos remember it for next time. */
    fun download(video: VideoItem?, sub: OsSubtitle, onReady: (File) -> Unit) {
        val generation = ++requestGeneration
        job?.cancel()
        if (video?.isVault == true) {
            _state.value = _state.value.copy(downloadingId = null)
            return
        }
        _state.value = _state.value.copy(downloadingId = sub.fileId, error = null)
        job = scope.launch {
            var bytes: ByteArray? = null
            try {
                bytes = client.download(sub.fileId).first
                val file = withContext(Dispatchers.IO) {
                    if (video != null && rememberable(video)) store.save(video.id, bytes!!)
                    else File(context.cacheDir, "os_${sub.fileId}.srt").also { it.writeBytes(bytes!!) }
                }
                currentCoroutineContext().ensureActive()
                if (generation == requestGeneration) {
                    _state.value = _state.value.copy(downloadingId = null)
                    onReady(file)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OsError) {
                if (generation == requestGeneration) _state.value = _state.value.copy(downloadingId = null, error = e)
            } catch (e: Exception) {
                if (generation == requestGeneration) _state.value = _state.value.copy(downloadingId = null, error = OsError.BadResponse)
            } finally {
                bytes?.fill(0)
            }
        }
    }

    fun rememberedFor(video: VideoItem): File? = if (rememberable(video)) store.find(video.id) else null

    private fun rememberable(video: VideoItem) = !video.isVault && video.id > 0L && video.folderPath != "Network Streams"

    private suspend fun hashOf(video: VideoItem): String? {
        if (video.isVault || video.uri.scheme.let { it != "content" && it != "file" }) return null
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(video.uri, "r")?.use { pfd ->
                    val channel = FileInputStream(pfd.fileDescriptor).channel
                    OpenSubtitlesHash.compute(pfd.statSize) { offset, length ->
                        val buf = ByteBuffer.allocate(length)
                        var pos = offset
                        while (buf.hasRemaining()) {
                            val n = channel.read(buf, pos)
                            if (n <= 0) break
                            pos += n
                        }
                        buf.array().copyOf(buf.position())
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
