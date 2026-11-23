package com.lnxv.player.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT * FROM videos WHERE isVault = 0 ORDER BY dateAddedSec DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT id FROM videos WHERE isVault = 0 AND folderPath = :folderPath ORDER BY title COLLATE NOCASE ASC")
    suspend fun getFolderVideoIds(folderPath: String): List<Long>

    @Query("SELECT * FROM videos WHERE isVault = 0 AND folderPath = :folderPath ORDER BY title ASC")
    fun getVideosInFolder(folderPath: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos")
    suspend fun getAllVideosOnce(): List<VideoEntity>

    @Query("DELETE FROM videos WHERE id IN (:ids)")
    suspend fun deleteVideoRowsByIds(ids: List<Long>)

    @Query("DELETE FROM playback_history WHERE videoId IN (:ids)")
    suspend fun deleteHistoryForVideos(ids: List<Long>)

    @Query("DELETE FROM playlist_items WHERE videoId IN (:ids)")
    suspend fun deletePlaylistItemsForVideos(ids: List<Long>)

    @Query("DELETE FROM video_playback_prefs WHERE videoId IN (:ids)")
    suspend fun deletePlaybackPrefsForVideos(ids: List<Long>)

    @Query("DELETE FROM vault_thumbs WHERE videoId IN (:ids)")
    suspend fun deleteThumbsForVideos(ids: List<Long>)

    @Transaction
    suspend fun deleteVideosByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        deleteHistoryForVideos(ids)
        deletePlaylistItemsForVideos(ids)
        deletePlaybackPrefsForVideos(ids)
        deleteThumbsForVideos(ids)
        deleteVideoRowsByIds(ids)
    }

    @Query("SELECT * FROM videos WHERE isVault = 0 AND lastPlayedTimestamp > 0 AND lastPositionMs > 5000 ORDER BY lastPlayedTimestamp DESC LIMIT 1")
    suspend fun getLastUnfinished(): VideoEntity?

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: Long): VideoEntity?

    @Query("SELECT * FROM videos WHERE isVault = 0 AND lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT 20")
    fun getRecentlyPlayedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isVault = 0 AND isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isVault = 1 ORDER BY dateAddedSec DESC")
    fun getVaultVideos(): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("UPDATE videos SET lastPositionMs = :positionMs, lastPlayedTimestamp = :timestamp WHERE id = :videoId")
    suspend fun updatePlaybackPosition(videoId: Long, positionMs: Long, timestamp: Long)

    @Query("UPDATE videos SET isFavorite = :isFavorite WHERE id = :videoId")
    suspend fun updateFavoriteStatus(videoId: Long, isFavorite: Boolean)

    @Query("UPDATE videos SET isVault = :isVault WHERE id = :videoId")
    suspend fun updateVaultStatus(videoId: Long, isVault: Boolean)

    @Query("UPDATE videos SET isVault = :isVault, vaultFile = :vaultFile WHERE id = :videoId")
    suspend fun setVaultState(videoId: Long, isVault: Boolean, vaultFile: String?)

    /** Atomically publishes the encrypted file reference and its optional encrypted thumbnail. */
    @Transaction
    suspend fun setVaultStateWithThumb(videoId: Long, vaultFile: String, thumb: VaultThumbEntity?) {
        setVaultState(videoId, true, vaultFile)
        if (thumb != null) insertThumb(thumb)
    }

    @Query("SELECT vaultFile FROM videos WHERE vaultFile IS NOT NULL")
    suspend fun getAllVaultFileNames(): List<String>

    @Query("SELECT COUNT(*) FROM videos WHERE isVault = 1 AND vaultFile IS NULL")
    fun countLegacyVault(): Flow<Int>

    @Query("SELECT * FROM videos WHERE isVault = 1 AND vaultFile IS NULL")
    suspend fun getLegacyVault(): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThumb(thumb: VaultThumbEntity)

    @Query("SELECT * FROM vault_thumbs WHERE videoId = :videoId")
    suspend fun getThumb(videoId: Long): VaultThumbEntity?

    @Query("DELETE FROM vault_thumbs WHERE videoId = :videoId")
    suspend fun deleteThumb(videoId: Long)

    @Query("DELETE FROM vault_thumbs")
    suspend fun clearThumbs()

    @Query("SELECT id FROM videos WHERE isVault = 1")
    suspend fun getVaultVideoIds(): List<Long>

    @Query("DELETE FROM videos WHERE isVault = 1")
    suspend fun deleteVaultVideoRows()

    @Transaction
    suspend fun deleteAllVaultRows() {
        val ids = getVaultVideoIds()
        if (ids.isEmpty()) return
        deleteHistoryForVideos(ids)
        deletePlaylistItemsForVideos(ids)
        deletePlaybackPrefsForVideos(ids)
        deleteThumbsForVideos(ids)
        deleteVaultVideoRows()
    }

    @Query("DELETE FROM videos WHERE id = :videoId")
    suspend fun deleteVideoRow(videoId: Long)

    @Transaction
    suspend fun deleteVideo(videoId: Long) {
        deleteHistory(videoId)
        deletePlaylistItemsForVideos(listOf(videoId))
        deletePlaybackPrefsForVideos(listOf(videoId))
        deleteThumb(videoId)
        deleteVideoRow(videoId)
    }

    @Query("SELECT * FROM playback_history ORDER BY lastWatchedTimestamp DESC")
    fun getPlaybackHistory(): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE videoId = :videoId")
    suspend fun deleteHistory(videoId: Long)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}
