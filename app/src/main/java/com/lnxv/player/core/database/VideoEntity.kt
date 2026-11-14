package com.lnxv.player.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val uriString: String,
    val path: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val dateAddedSec: Long,
    val folderPath: String,
    val folderName: String,
    val lastPositionMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val isVault: Boolean = false,
    /** File name inside filesDir/lnxv_vault for encrypted vault items; null for legacy/plain items. */
    val vaultFile: String? = null
)

/** Small encrypted thumbnail (ChunkedAead container) so the vault grid works without decrypting videos. */
@Entity(tableName = "vault_thumbs")
class VaultThumbEntity(
    @PrimaryKey val videoId: Long,
    val data: ByteArray
)

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val videoId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val videoId: Long,
    val addedTimestamp: Long
)
