package com.lnxv.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.lnxv.player.core.datastore.UserPreferences
import com.lnxv.player.core.design.LNXVTheme
import com.lnxv.player.core.flags.FeatureFlag
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.core.util.IncomingIntentValidator
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.lnxv.player.playback.pip.PipManager
import com.lnxv.player.ui.navigation.LNXVNavigationHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** null until DataStore delivered the first value; the splash screen is held until then (no runBlocking). */
    private val prefsState = MutableStateFlow<UserPreferences?>(null)
    @Volatile private var backgroundPlayEnabled = false
    @Volatile private var autoPipEnabled = true
    @Volatile private var autoLockMs = 0L
    @Volatile private var autoLockEnabled = true
    private var globalSecure = false
    private var lockJob: Job? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        if (isGranted) {
            val app = application as LNXVApplication
            app.appScope.launch(Dispatchers.IO) { app.videoRepository.refreshLibrary() }
        }
    }

    /** MediaStore delete confirmation for the original video after it was encrypted into the vault. */
    private val deleteConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        val app = application as LNXVApplication
        app.appScope.launch(Dispatchers.IO) { app.videoRepository.refreshLibrary() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { prefsState.value == null }

        requestHighestRefreshRate()
        checkAndRequestPermissions()

        val app = application as LNXVApplication

        lifecycleScope.launch {
            app.dataStore.userPreferencesFlow.collect {
                backgroundPlayEnabled = it.isBackgroundPlayEnabled
                autoPipEnabled = it.isAutoPiPEnabled
                autoLockMs = it.vaultAutoLockMs
                autoLockEnabled = it.isEnabled(FeatureFlag.VAULT_AUTO_LOCK)
                applyGlobalSecure(it.blockScreenshotsEverywhere)
                prefsState.value = it
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    app.vaultTransfers.messages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
                }
                launch {
                    app.vaultTransfers.deleteConsents.collect { sender ->
                        deleteConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                }
            }
        }

        if (savedInstanceState == null) handleViewIntent(intent)

        setContent {
            val prefs by prefsState.collectAsState()
            val p = prefs
            if (p != null) {
                val systemDark = isSystemInDarkTheme()
                val darkTheme = when (p.themeMode) {
                    "LIGHT" -> false
                    "SYSTEM" -> systemDark
                    else -> true
                }
                LNXVTheme(darkTheme = darkTheme, amoled = p.isAmoled) {
                    val navController = rememberNavController()
                    LNXVNavigationHost(navController = navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    /** Opens a video handed over by another app (VIEW intent): validated, played transiently, not saved to Room. */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (!IncomingIntentValidator.isAllowedViewData(uri.scheme, intent.type)) return
        val app = application as LNXVApplication
        lifecycleScope.launch {
            val prefs = app.dataStore.userPreferencesFlow.first()
            if (!prefs.isEnabled(FeatureFlag.OPEN_WITH_INTENTS)) return@launch
            val known = withContext(Dispatchers.IO) { findLibraryVideoId(app, uri) }
            if (known != null) { // already in the library: reuse it so resume position works
                app.pendingOpenVideoId.value = known
                return@launch
            }
            if (uri.scheme == "content" && (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            val title = withContext(Dispatchers.IO) { displayNameOf(uri) }
            app.transientVideo = VideoItem(
                id = LNXVApplication.TRANSIENT_VIDEO_ID,
                title = title,
                uri = uri,
                path = if (uri.scheme == "file") uri.path.orEmpty() else "",
                durationMs = 0L, sizeBytes = 0L, width = 0, height = 0,
                mimeType = intent.type ?: "video/*",
                dateAddedSec = System.currentTimeMillis() / 1000,
                folderPath = "Opened with", folderName = "Opened with"
            )
            app.pendingOpenVideoId.value = LNXVApplication.TRANSIENT_VIDEO_ID
        }
    }

    private suspend fun findLibraryVideoId(app: LNXVApplication, uri: Uri): Long? {
        if (uri.scheme != "content" || uri.authority != "media") return null
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        return if (app.videoRepository.getVideoById(id) != null) id else null
    }

    private fun displayNameOf(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
    }

    private fun applyGlobalSecure(enabled: Boolean) {
        (application as LNXVApplication).screenshotsBlockedGlobally = enabled
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else if (globalSecure) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        globalSecure = enabled
    }

    @Suppress("DEPRECATION")
    private fun requestHighestRefreshRate() {
        try {
            val display = windowManager.defaultDisplay
            val current = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            if (best.refreshRate > current.refreshRate) {
                val lp = window.attributes
                lp.preferredDisplayModeId = best.modeId
                window.attributes = lp
            }
        } catch (e: Exception) {
            // Best effort only
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onStart() {
        super.onStart()
        lockJob?.cancel()
        lockJob = null
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // If playing video, enter PiP mode automatically
        val app = application as LNXVApplication
        if (!app.isPlayerInitialized) return
        // Only auto-enter PiP from the video screen, and only if the user enabled it
        if (!app.playerManager.isVideoScreenActive || !autoPipEnabled) return
        val player = app.playerManager.exoPlayer
        if (player.isPlaying && PipManager.isPipSupported(this)) {
            val format = player.videoFormat
            val width = format?.width ?: 16
            val height = format?.height ?: 9
            PipManager.enterPipMode(this, width, height)
        }
    }

    override fun onStop() {
        super.onStop()
        val app = application as LNXVApplication
        scheduleVaultAutoLock(app)
        if (!app.isPlayerInitialized) return
        val manager = app.playerManager
        if (isInPictureInPictureMode) {
            // PiP window was dismissed by the user: stop playback completely
            manager.stopPlayback()
        } else if (!backgroundPlayEnabled) {
            manager.pause()
        }
    }

    private fun scheduleVaultAutoLock(app: LNXVApplication) {
        if (isChangingConfigurations || !autoLockEnabled || !app.vaultKeyManager.isUnlocked) return
        lockJob?.cancel()
        val delayMs = autoLockMs
        if (delayMs <= 0L) {
            app.lockVault()
        } else {
            lockJob = app.appScope.launch {
                delay(delayMs)
                app.lockVault()
            }
        }
    }

    override fun onDestroy() {
        val app = application as LNXVApplication
        if (isFinishing) {
            app.lockVault()
            if (app.isPlayerInitialized) app.playerManager.stopPlayback()
        }
        super.onDestroy()
    }
}
