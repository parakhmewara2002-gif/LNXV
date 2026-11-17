package com.lnxv.player.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Per-video playback choices (DB v4). Never written for vault videos.
 * [decoderMode] holds a DecoderMode name or null when the video follows the global default.
 */
@Entity(tableName = "video_playback_prefs")
data class VideoPlaybackPrefsEntity(
    @PrimaryKey val videoId: Long,
    val decoderMode: String?,
    val audioTrackId: String?,
    val subtitleTrackId: String?,
    val subtitlesOff: Boolean
)

@Dao
interface VideoPrefsDao {
    @Query("SELECT * FROM video_playback_prefs WHERE videoId = :videoId LIMIT 1")
    suspend fun get(videoId: Long): VideoPlaybackPrefsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VideoPlaybackPrefsEntity)

    @Query("DELETE FROM video_playback_prefs WHERE videoId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
