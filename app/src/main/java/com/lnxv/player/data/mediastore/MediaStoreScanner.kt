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

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED
        )

        // Only query valid video files
        val selection = "${MediaStore.Video.Media.DURATION} > 0"
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
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: "Video_$id"
                    val path = c.getString(dataCol) ?: ""
                    val duration = c.getLong(durationCol)
                    val size = c.getLong(sizeCol)
                    val width = c.getInt(widthCol)
                    val height = c.getInt(heightCol)
                    val mime = c.getString(mimeCol) ?: "video/mp4"
                    val dateAdded = c.getLong(dateCol)

                    val contentUri = ContentUris.withAppendedId(collection, id)

                    val parentFile = File(path).parentFile
                    val folderPath = parentFile?.absolutePath ?: "Internal Storage"
                    val folderName = parentFile?.name ?: "Videos"

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
            e.printStackTrace()
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
