package com.lnxv.player.data.repository

import android.content.Context
import android.net.Uri
import com.lnxv.player.core.database.PlaybackHistoryEntity
import com.lnxv.player.core.database.VideoDao
import com.lnxv.player.core.database.VideoEntity
import com.lnxv.player.core.model.FolderItem
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.data.mediastore.MediaStoreScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VideoRepository(
    private val context: Context,
    private val videoDao: VideoDao
) {
    private val scanner = MediaStoreScanner(context)

    val allVideosFlow: Flow<List<VideoItem>> = videoDao.getAllVideos().map { entities ->
        entities.map { it.toModel() }
    }

    val recentlyPlayedFlow: Flow<List<VideoItem>> = videoDao.getRecentlyPlayedVideos().map { entities ->
        entities.map { it.toModel() }
    }

    val favoritesFlow: Flow<List<VideoItem>> = videoDao.getFavoriteVideos().map { entities ->
        entities.map { it.toModel() }
    }

    suspend fun refreshLibrary() = withContext(Dispatchers.IO) {
        val scanned = scanner.scanLocalVideos()
        if (scanned.isEmpty()) return@withContext // e.g. permission denied: never wipe the library
        val existing = videoDao.getAllVideosOnce().associateBy { it.id }
        // Keep user data (resume position, history stamp, favourite, vault) for known videos
        val changed = scanned.mapNotNull { s ->
            val old = existing[s.id]
            if (old == null) {
                s
            } else {
                val merged = s.copy(
                    lastPositionMs = old.lastPositionMs,
                    lastPlayedTimestamp = old.lastPlayedTimestamp,
                    isFavorite = old.isFavorite,
                    isVault = old.isVault,
                    vaultFile = old.vaultFile
                )
                if (merged == old) null else merged
            }
        }
        if (changed.isNotEmpty()) videoDao.insertVideos(changed)
        // Remove videos that no longer exist on the device (vault items are kept)
        val scannedIds = scanned.mapTo(HashSet()) { it.id }
        existing.values.filter { !it.isVault && it.id !in scannedIds }
            .map { it.id }
            .chunked(500)
            .forEach { videoDao.deleteVideosByIds(it) }
    }

    fun getVideosInFolder(folderPath: String): Flow<List<VideoItem>> {
        return videoDao.getVideosInFolder(folderPath).map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun getFolderVideoIds(folderPath: String): List<Long> = withContext(Dispatchers.IO) {
        videoDao.getFolderVideoIds(folderPath)
    }

    suspend fun getVideoById(id: Long): VideoItem? = withContext(Dispatchers.IO) {
        videoDao.getVideoById(id)?.toModel()
    }

    suspend fun updatePlaybackPosition(videoId: Long, positionMs: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        videoDao.updatePlaybackPosition(videoId, positionMs, now)
        videoDao.insertHistory(
            PlaybackHistoryEntity(
                videoId = videoId,
                positionMs = positionMs,
                durationMs = 0L,
                lastWatchedTimestamp = now
            )
        )
    }

    suspend fun toggleFavorite(videoId: Long, currentStatus: Boolean) = withContext(Dispatchers.IO) {
        videoDao.updateFavoriteStatus(videoId, !currentStatus)
    }

    suspend fun deleteVideo(videoId: Long) = withContext(Dispatchers.IO) {
        videoDao.deleteVideo(videoId)
        videoDao.deleteHistory(videoId)
    }

    private fun VideoEntity.toModel(): VideoItem {
        return VideoItem(
            id = id,
            title = title,
            uri = if (vaultFile != null) com.lnxv.player.playback.engine.VaultRoutingDataSource.uriFor(vaultFile) else Uri.parse(uriString),
            path = path,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            mimeType = mimeType,
            dateAddedSec = dateAddedSec,
            folderPath = folderPath,
            folderName = folderName,
            lastPositionMs = lastPositionMs,
            lastPlayedTimestamp = lastPlayedTimestamp,
            isFavorite = isFavorite,
            isVault = isVault,
            vaultFile = vaultFile
        )
    }
}
