package com.lnxv.player.data.repository

import android.content.Context
import android.net.Uri
import com.lnxv.player.core.database.VideoDao
import com.lnxv.player.core.database.VideoEntity
import com.lnxv.player.core.datastore.UserPreferencesDataStore
import com.lnxv.player.core.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class VaultRepository(
    private val context: Context,
    private val videoDao: VideoDao,
    private val dataStore: UserPreferencesDataStore
) {
    private val vaultDir: File by lazy {
        val dir = File(context.filesDir, "lnxv_vault")
        if (!dir.exists()) dir.mkdirs()
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) noMedia.createNewFile()
        dir
    }

    val vaultVideosFlow: Flow<List<VideoItem>> = videoDao.getVaultVideos().map { entities ->
        entities.map { it.toModel() }
    }

    suspend fun isPinSet(): Boolean = withContext(Dispatchers.IO) {
        val prefs = dataStore.userPreferencesFlow.first()
        prefs.vaultPinHash.isNotEmpty()
    }

    suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        dataStore.setVaultPin(pin)
    }

    suspend fun verifyPin(enteredPin: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = dataStore.userPreferencesFlow.first()
        dataStore.verifyVaultPin(enteredPin, prefs.vaultPinHash)
    }

    suspend fun moveToVault(videoId: Long) = withContext(Dispatchers.IO) {
        videoDao.updateVaultStatus(videoId, true)
    }

    suspend fun restoreFromVault(videoId: Long) = withContext(Dispatchers.IO) {
        videoDao.updateVaultStatus(videoId, false)
    }

    private fun VideoEntity.toModel(): VideoItem {
        return VideoItem(
            id = id,
            title = title,
            uri = Uri.parse(uriString),
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
            isVault = isVault
        )
    }
}
