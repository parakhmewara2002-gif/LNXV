package com.lnxv.player.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "videoId"])
data class PlaylistItemEntity(
    val playlistId: Long,
    val videoId: Long,
    val position: Int
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val itemCount: Int
)

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id AS id, p.name AS name, " +
            "(SELECT COUNT(*) FROM playlist_items i JOIN videos v ON v.id = i.videoId " +
            "WHERE i.playlistId = p.id AND v.isVault = 0) AS itemCount " +
            "FROM playlists p ORDER BY p.createdAt DESC"
    )
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItemEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    /** Vault videos are never exposed through playlists. */
    @Query(
        "SELECT i.videoId FROM playlist_items i JOIN videos v ON v.id = i.videoId " +
            "WHERE i.playlistId = :playlistId AND v.isVault = 0 ORDER BY i.position ASC"
    )
    suspend fun getVideoIds(playlistId: Long): List<Long>

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeItem(playlistId: Long, videoId: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearItems(playlistId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String)

    @Query("DELETE FROM playlist_items WHERE videoId = :videoId")
    suspend fun removeVideoEverywhere(videoId: Long)
}
