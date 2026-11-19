package com.lnxv.player.data.thumb

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.ImageRequest
import coil.request.Options
import coil.request.videoFrameMillis
import com.lnxv.player.core.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Fast list thumbnails. On Android 10+ MediaStore already keeps a thumbnail cache, so ContentResolver.loadThumbnail
 * returns in a few ms instead of decoding a video frame per row (Coil's VideoFrameDecoder), which was the main cost
 * of fast scrolling through a long list. Falls back to one MediaMetadataRetriever frame when no system thumbnail exists.
 */
data class VideoThumb(val uri: Uri)

class VideoThumbKeyer : Keyer<VideoThumb> {
    override fun key(data: VideoThumb, options: Options): String = "vthumb:${data.uri}"
}

class VideoThumbFetcher(private val context: Context, private val data: VideoThumb) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext<FetchResult?>(Dispatchers.IO) {
        val bmp = systemThumbnail() ?: frameFallback() ?: throw IOException("No thumbnail for ${data.uri}")
        DrawableResult(BitmapDrawable(context.resources, bmp), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun systemThumbnail(): Bitmap? =
        if (Build.VERSION.SDK_INT >= 29) runCatching { context.contentResolver.loadThumbnail(data.uri, Size(W, H), null) }.getOrNull() else null

    private fun frameFallback(): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, data.uri)
            if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, W, H)
            else r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<VideoThumb> {
        override fun create(data: VideoThumb, options: Options, imageLoader: ImageLoader): Fetcher = VideoThumbFetcher(context, data)
    }

    private companion object { const val W = 360; const val H = 240 }
}

/** Request for a list row thumbnail: fast MediaStore path for normal library videos, legacy frame decode otherwise. */
fun videoThumbRequest(context: Context, video: VideoItem): ImageRequest {
    val builder = ImageRequest.Builder(context).size(coil.size.Size(360, 240)).crossfade(120)
    return if (!video.isVault && Build.VERSION.SDK_INT >= 29 && video.uri.scheme == "content") {
        builder.data(VideoThumb(video.uri)).build()
    } else {
        builder.data(video.uri).videoFrameMillis(1000).build()
    }
}
