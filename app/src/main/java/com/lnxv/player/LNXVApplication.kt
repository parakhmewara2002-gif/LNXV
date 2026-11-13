package com.lnxv.player

import android.app.Application
import coil.ImageLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.lnxv.player.core.security.KeystoreKeyWrapper
import com.lnxv.player.core.security.VaultKeyManager
import com.lnxv.player.data.repository.VaultFiles
import com.lnxv.player.data.repository.VaultTransferManager
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
    /** App-level scope with SupervisorJob; no unscoped CoroutineScope(Dispatchers.IO).launch anywhere. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vaultKeyManager by lazy { VaultKeyManager(VaultFiles.keyFile(this), KeystoreKeyWrapper()) }
    val vaultRepository by lazy { VaultRepository(this, database.videoDao(), dataStore, vaultKeyManager) }
    val vaultTransfers by lazy { VaultTransferManager(appScope, vaultRepository) }
    private val playerManagerDelegate = lazy { Media3PlayerManager(this) }
    val playerManager by playerManagerDelegate
    /** Mirrors the "Block screenshots everywhere" setting so screens can restore window flags correctly. */
    @Volatile var screenshotsBlockedGlobally: Boolean = false
    val isPlayerInitialized: Boolean get() = playerManagerDelegate.isInitialized()

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) enableStrictMode()
        registerScreenOffLock()
        // Warm up DataStore off the main thread
        appScope.launch(Dispatchers.IO) { dataStore.userPreferencesFlow.first() }
    }

    /** Transient "Open with" video (never written to Room); played via route id [TRANSIENT_VIDEO_ID]. */
    @Volatile var transientVideo: com.lnxv.player.core.model.VideoItem? = null

    /** Explicit play queue (e.g. a playlist) consumed once by the next player session; null = use the folder queue. */
    @Volatile var queueOverride: List<Long>? = null

    /** Set by MainActivity when a VIEW intent was accepted; consumed (set back to null) by the nav host. */
    val pendingOpenVideoId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    /** True while a vault video is loaded in the shared player (drives FLAG_SECURE and lock handling). */
    @Volatile var playingVaultItem: Boolean = false

    private val _lockEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val lockEvents: kotlinx.coroutines.flow.SharedFlow<Unit> = _lockEvents

    /**
     * Locks the vault session: cancels copy/restore jobs, zeroes the in-memory key, stops vault playback
     * and tells the UI to drop vault screens from the back stack.
     */
    fun lockVault() {
        appScope.launch(Dispatchers.Main) {
            if (!vaultKeyManager.isUnlocked) return@launch
            vaultTransfers.cancelAll()
            if (playingVaultItem && isPlayerInitialized) playerManager.stopPlayback()
            vaultRepository.lock()
            playingVaultItem = false
            _lockEvents.tryEmit(Unit)
        }
    }

    private fun registerScreenOffLock() {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == android.content.Intent.ACTION_SCREEN_OFF) lockVault()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this, receiver, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun enableStrictMode() {
        android.os.StrictMode.setThreadPolicy(
            android.os.StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
        )
        android.os.StrictMode.setVmPolicy(
            android.os.StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build()
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
        }
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
        const val TRANSIENT_VIDEO_ID = -1L
        lateinit var instance: LNXVApplication
            private set
    }
}
