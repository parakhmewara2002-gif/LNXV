package com.lnxv.player

import android.app.Application
import coil.ImageLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lnxv.player.core.database.LNXVDatabase
import com.lnxv.player.core.datastore.UserPreferencesDataStore
import com.lnxv.player.data.repository.VaultRepository
import com.lnxv.player.data.repository.VideoRepository
import com.lnxv.player.playback.engine.Media3PlayerManager

class LNXVApplication : Application(), ImageLoaderFactory {

    val database by lazy { LNXVDatabase.getInstance(this) }
    val dataStore by lazy { UserPreferencesDataStore(this) }
    val videoRepository by lazy { VideoRepository(this, database.videoDao()) }
    val vaultRepository by lazy { VaultRepository(this, database.videoDao(), dataStore) }
    private val playerManagerDelegate = lazy { Media3PlayerManager(this) }
    val playerManager by playerManagerDelegate
    val isPlayerInitialized: Boolean get() = playerManagerDelegate.isInitialized()

    override fun onCreate() {
        super.onCreate()
        instance = this
        CoroutineScope(Dispatchers.IO).launch { dataStore.userPreferencesFlow.first() }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("lnxv_thumbnail_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: LNXVApplication
            private set
    }
}
