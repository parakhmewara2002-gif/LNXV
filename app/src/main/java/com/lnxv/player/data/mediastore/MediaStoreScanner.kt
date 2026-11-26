package com.lnxv.player.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import com.lnxv.player.core.database.VideoEntity
import com.lnxv.player.core.model.FolderItem
import com.lnxv.player.core.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {

    suspend fun scanLocalVideos(): List<VideoEntity> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<VideoEntity>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val useRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            // DATA is deprecated on Android 10+ (and can be null there); RELATIVE_PATH is the supported folder info.
            add(MediaStore.Video.Media.DATA)
            if (useRelativePath) add(MediaStore.Video.Media.RELATIVE_PATH)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.DATE_ADDED)
        }.toTypedArray()

        // SIZE > 0 instead of DURATION > 0: some valid containers report duration 0 before they are indexed/played.
        val selection = "${MediaStore.Video.Media.SIZE} > 0"
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = c.getColumnIndex(MediaStore.Video.Media.DATA)
                val relCol = if (useRelativePath) c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: "Video_$id"
                    val rawPath = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                    val relPath = if (relCol >= 0) c.getString(relCol) ?: "" else ""
                    val path = rawPath
                    val duration = c.getLong(durationCol)
                    val size = c.getLong(sizeCol)
                    val width = c.getInt(widthCol)
                    val height = c.getInt(heightCol)
                    val mime = c.getString(mimeCol) ?: "video/mp4"
                    val dateAdded = c.getLong(dateCol)

                    val contentUri = ContentUris.withAppendedId(collection, id)

                    val parentFile = if (rawPath.isNotEmpty()) File(rawPath).parentFile else null
                    val folderPath = parentFile?.absolutePath
                        ?: relPath.trimEnd('/').ifEmpty { "Internal Storage" }
                    val folderName = parentFile?.name
                        ?: relPath.trimEnd('/').substringAfterLast('/').ifEmpty { "Videos" }

                    videoList.add(
                        VideoEntity(
                            id = id,
                            title = name,
                            uriString = contentUri.toString(),
                            path = path,
                            durationMs = duration,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            mimeType = mime,
                            dateAddedSec = dateAdded,
                            folderPath = folderPath,
                            folderName = folderName,
                            lastPositionMs = 0L,
                            lastPlayedTimestamp = 0L,
                            isFavorite = false,
                            isVault = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MediaStoreScanner", "Failed to query media store", e)
        }

        videoList
    }

    companion object {
        fun extractFolders(videos: List<VideoItem>): List<FolderItem> {
            return videos
                .groupBy { it.folderPath }
                .map { (path, vids) ->
                    FolderItem(
                        path = path,
                        name = vids.firstOrNull()?.folderName ?: File(path).name,
                        videoCount = vids.size,
                        totalSizeBytes = vids.sumOf { it.sizeBytes }
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
    }
}
