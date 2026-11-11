package com.lnxv.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.lnxv.player.core.design.LNXVTheme
import com.lnxv.player.playback.pip.PipManager
import com.lnxv.player.ui.navigation.LNXVNavigationHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val prefScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var backgroundPlayEnabled = false
    @Volatile private var autoPipEnabled = true

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        if (isGranted) {
            val repo = (application as LNXVApplication).videoRepository
            CoroutineScope(Dispatchers.IO).launch {
                repo.refreshLibrary()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestHighestRefreshRate()
        checkAndRequestPermissions()

        val app = application as LNXVApplication
        val initialPrefs = runBlocking { app.dataStore.userPreferencesFlow.first() }
        backgroundPlayEnabled = initialPrefs.isBackgroundPlayEnabled
        autoPipEnabled = initialPrefs.isAutoPiPEnabled
        prefScope.launch {
            app.dataStore.userPreferencesFlow.collect {
                backgroundPlayEnabled = it.isBackgroundPlayEnabled
                autoPipEnabled = it.isAutoPiPEnabled
            }
        }

        setContent {
            val prefs by app.dataStore.userPreferencesFlow.collectAsState(initial = initialPrefs)
            LNXVTheme(darkTheme = prefs.isDarkMode, amoled = prefs.isAmoled) {
                val navController = rememberNavController()
                LNXVNavigationHost(navController = navController)
            }
        }
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
        if (!app.isPlayerInitialized) return
        val manager = app.playerManager
        if (isInPictureInPictureMode) {
            // PiP window was dismissed by the user: stop playback completely
            manager.stopPlayback()
        } else if (!backgroundPlayEnabled) {
            manager.pause()
        }
    }

    override fun onDestroy() {
        val app = application as LNXVApplication
        if (isFinishing && app.isPlayerInitialized) {
            app.playerManager.stopPlayback()
        }
        prefScope.cancel()
        super.onDestroy()
    }
}
