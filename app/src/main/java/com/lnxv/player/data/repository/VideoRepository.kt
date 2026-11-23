package com.lnxv.player.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.net.Uri
import com.lnxv.player.core.database.PlaybackHistoryEntity
import com.lnxv.player.core.database.VideoDao
import com.lnxv.player.core.database.VideoEntity
import com.lnxv.player.core.model.FolderItem
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.data.mediastore.MediaStoreScanner
import com.lnxv.player.core.subtitle.online.OnlineSubtitleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VideoRepository(
    private val context: Context,
    private val videoDao: VideoDao
) {
    private val scanner = MediaStoreScanner(context)
    private val subtitleStore = OnlineSubtitleStore(context)

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
        val scan = scanner.scanLocalVideos()
        if (!scan.succeeded) return@withContext // Query failure/permission denial is not an empty library.
        val scanned = scan.videos
        val existing = videoDao.getAllVideosOnce().associateBy { it.id }
        // Keep user data (resume position, history stamp, favourite, vault) for known videos
        val changed = scanned.mapNotNull { s ->
            val old = existing[s.id]
            if (old == null) {
                s
            } else {
                val sameMedia = old.path == s.path &&
                    old.dateAddedSec == s.dateAddedSec &&
                    (old.sizeBytes == s.sizeBytes || old.sizeBytes <= 0L || s.sizeBytes <= 0L)
                val merged = if (sameMedia) {
                    s.copy(
                        lastPositionMs = old.lastPositionMs,
                        lastPlayedTimestamp = old.lastPlayedTimestamp,
                        isFavorite = old.isFavorite,
                        isVault = old.isVault,
                        vaultFile = old.vaultFile
                    )
                } else {
                    s
                }
                if (merged == old) null else merged
            }
        }
        if (changed.isNotEmpty()) videoDao.insertVideos(changed)
        // Remove videos that no longer exist on the device (vault items are kept)
        val scannedIds = scanned.mapTo(HashSet()) { it.id }
        // Safeguard against transient MediaStore scanner drops / partial unmounts:
        // Only prune missing videos if scanner returned at least 80% of the existing library
        val candidates = existing.values.filter { !it.isVault }
        // Android 14+ can return only user-selected videos. A partial grant is never a complete
        // library snapshot, even when the library is small, so it must not prune hidden rows.
        val hasFullAccess = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (scanned.isEmpty()) {
            if (hasFullAccess && candidates.isNotEmpty()) {
                android.util.Log.w("VideoRepository", "Empty full-access scan; preserving existing library metadata")
            }
            return@withContext
        }
        if (com.lnxv.player.core.model.LibrarySyncPolicy.shouldPrune(scannedIds.size, candidates.size, hasFullAccess)) {
            val staleIds = candidates.filter { it.id !in scannedIds }.map { it.id }
            staleIds.chunked(500).forEach { videoDao.deleteVideosByIds(it) }
            staleIds.forEach(subtitleStore::forget)
        }
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
        subtitleStore.forget(videoId)
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
