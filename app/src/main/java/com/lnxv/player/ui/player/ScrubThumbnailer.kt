package com.lnxv.player.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Frame preview for the seek bar. Replaces one Coil request per drag step (each re-opened the file and was cancelled by the
 * next step before it could finish, so the preview stayed blank on heavy 1080p/HEVC files).
 *  - ONE MediaMetadataRetriever per video, opened lazily on the first request and reused for every frame,
 *  - keyframe-only decode (OPTION_CLOSEST_SYNC), 320x180,
 *  - a single worker thread; only the newest requested position is decoded, older ones are dropped,
 *  - [frame] always keeps the last decoded bitmap, so the preview never flashes blank between steps,
 *  - small LRU of decoded seconds.
 */
class ScrubThumbnailer(private val context: Context, private val uri: Uri) {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "scrub-thumb").apply { isDaemon = true } }
    private val scope = CoroutineScope(executor.asCoroutineDispatcher())
    private val cache = LruCache<Long, Bitmap>(48)
    private var retriever: MediaMetadataRetriever? = null
    private var failed = false
    private var job: Job? = null

    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame

    fun request(positionMs: Long) {
        if (failed) return
        val second = (positionMs / 1000L).coerceAtLeast(0L)
        cache.get(second)?.let { _frame.value = it; return }
        job?.cancel()
        job = scope.launch {
            if (!isActive) return@launch
            val r = retriever ?: try {
                MediaMetadataRetriever().also { it.setDataSource(context, uri); retriever = it }
            } catch (e: Exception) {
                failed = true
                return@launch
            }
            val bmp = try {
                val us = second * 1_000_000L
                if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, W, H)
                else r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) {
                null
            }
            if (bmp != null) {
                cache.put(second, bmp)
                if (isActive) _frame.value = bmp
            }
        }
    }

    /** Cancels pending work and releases the retriever after any in-flight decode has finished (on the worker thread). */
    fun release() {
        job?.cancel()
        executor.execute { runCatching { retriever?.release() }; retriever = null }
        executor.shutdown()
    }

    private companion object { const val W = 320; const val H = 180 }
}
