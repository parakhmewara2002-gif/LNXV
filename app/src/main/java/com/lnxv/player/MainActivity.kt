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

class MainActivity : androidx.fragment.app.FragmentActivity() {

    /** null until DataStore delivered the first value; the splash screen is held until then (no runBlocking). */
    private val prefsState = MutableStateFlow<UserPreferences?>(null)
    @Volatile private var backgroundPlayEnabled = false
    @Volatile private var autoPipEnabled = true
    @Volatile private var autoLockMs = 0L
    @Volatile private var autoLockEnabled = true
    private var globalSecure = false
    private val secureWindowReasons = mutableSetOf<String>()
    private var lockJob: Job? = null
    private val deviceSpecs: com.lnxv.player.core.design.DeviceTier.Specs by lazy {
        @Suppress("DEPRECATION")
        val hz = runCatching { windowManager.defaultDisplay.supportedModes.maxOf { it.refreshRate } }.getOrDefault(60f)
        com.lnxv.player.core.design.DeviceTier.specs(this, hz)
    }
    private val lowEndDevice: Boolean get() = deviceSpecs.lowEnd

    /** System "Remove animations" (animator duration scale 0) is treated as "reduce effects". */
    private fun systemAnimationsDisabled(): Boolean = runCatching {
        android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    private val backgroundConnector by lazy { com.lnxv.player.playback.service.BackgroundPlaybackConnector(this) }
    private var askedNotificationPermission = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Denied is fine: playback and media-session controls still work, only the notification is hidden. */ }

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
        splash.setOnExitAnimationListener { splashScreenView ->
            if (systemAnimationsDisabled()) {
                splashScreenView.remove()
            } else {
            val iconView = splashScreenView.iconView
            val targetView = iconView ?: splashScreenView.view
            val scaleX = android.animation.ObjectAnimator.ofFloat(targetView, android.view.View.SCALE_X, 1f, 1.25f, 0.8f, 0f)
            val scaleY = android.animation.ObjectAnimator.ofFloat(targetView, android.view.View.SCALE_Y, 1f, 1.25f, 0.8f, 0f)
            val alpha = android.animation.ObjectAnimator.ofFloat(splashScreenView.view, android.view.View.ALPHA, 1f, 0f)
            val animatorSet = android.animation.AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = 320L
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        splashScreenView.remove()
                    }
                })
            }
            animatorSet.start()
            }
        }
        // Build ExoPlayer + codec renderers while the main thread is idle on Home, so the first video does not pay for it.
        android.os.Looper.myQueue().addIdleHandler {
            if (!isFinishing && !isDestroyed) runCatching { (application as LNXVApplication).playerManager }
            false
        }

        requestHighestRefreshRate()
        checkAndRequestPermissions()

        val app = application as LNXVApplication

        lifecycleScope.launch {
            app.dataStore.userPreferencesFlow.collect {
                backgroundPlayEnabled = it.isBackgroundPlayEnabled
                updateBackgroundPlayback(it.isBackgroundPlayEnabled)
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
            val navController = rememberNavController()
            val prefs by prefsState.collectAsState()
            val p = prefs
            if (p != null) {
                val systemDark = isSystemInDarkTheme()
                val darkTheme = when (p.themeMode) {
                    "LIGHT" -> false
                    "SYSTEM" -> systemDark
                    else -> true
                }
                val reduce = com.lnxv.player.core.design.DeviceTier.resolveReduceEffects(
                    com.lnxv.player.core.design.EffectsMode.parse(p.effectsMode), lowEndDevice
                ) || systemAnimationsDisabled()
                val glassProfile = com.lnxv.player.core.design.GlassProfileResolver.resolve(
                    mode = com.lnxv.player.core.design.EffectsMode.parse(p.effectsMode),
                    lowEnd = lowEndDevice,
                    apiLevel = android.os.Build.VERSION.SDK_INT,
                    totalRamBytes = deviceSpecs.totalRamBytes,
                    maxRefreshHz = deviceSpecs.maxRefreshHz,
                    systemAnimationsOff = systemAnimationsDisabled()
                )
                LNXVTheme(darkTheme = darkTheme, amoled = p.isAmoled, dynamicColor = p.dynamicColor, reduceEffects = reduce, glassProfile = glassProfile) {
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
        if (intent?.action == ACTION_CONTINUE) { handleContinue(); return }
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (!IncomingIntentValidator.isAllowedViewData(uri.scheme, intent.type)) return
        if (uri.scheme == "file") { // never open files from our own private storage (database, vault key, vault files)
            val target = runCatching { java.io.File(uri.path.orEmpty()).canonicalPath }.getOrNull() ?: return
            val privateRoots = listOf(filesDir, cacheDir, dataDir).map { it.canonicalPath }
            if (IncomingIntentValidator.isUnderAny(target, privateRoots)) return
        }
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

    /** App shortcut / widget: resume the last unfinished library video (vault items are never offered). */
    private fun handleContinue() {
        val app = application as LNXVApplication
        lifecycleScope.launch {
            val v = withContext(Dispatchers.IO) { app.database.videoDao().getLastUnfinished() } ?: return@launch
            app.pendingOpenVideoId.value = v.id
        }
    }

    companion object {
        const val ACTION_CONTINUE = "com.lnxv.player.action.CONTINUE"
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

    /** Connects the media session service only while background play is on; asks for notifications lazily. */
    private fun updateBackgroundPlayback(enabled: Boolean) {
        if (!enabled) { backgroundConnector.disconnect(); return }
        backgroundConnector.connect()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotificationPermission &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Multiple UI surfaces may require FLAG_SECURE at once; one surface must not clear another's protection. */
    fun setSecureWindowReason(reason: String, enabled: Boolean) {
        if (enabled) secureWindowReasons.add(reason) else secureWindowReasons.remove(reason)
        refreshSecureWindowFlag()
    }

    private fun refreshSecureWindowFlag() {
        if (globalSecure || secureWindowReasons.isNotEmpty()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun applyGlobalSecure(enabled: Boolean) {
        (application as LNXVApplication).screenshotsBlockedGlobally = enabled
        globalSecure = enabled
        refreshSecureWindowFlag()
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
            if (Build.VERSION.SDK_INT >= 34) permissions.add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Android 14 "Select photos and videos": partial access is enough, do not ask again on every launch.
        val partialAccess = Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED
        val needed = if (partialAccess) emptyList() else permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            // Explain why before the system dialog, once (first run only).
            lifecycleScope.launch {
                val done = (application as LNXVApplication).dataStore.userPreferencesFlow.first().onboardingDone
                if (done) {
                    permissionLauncher.launch(needed.toTypedArray())
                } else {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.onboarding_title))
                        .setMessage(getString(R.string.onboarding_message))
                        .setCancelable(false)
                        .setPositiveButton(getString(R.string.action_continue)) { _, _ ->
                            lifecycleScope.launch {
                                (application as LNXVApplication).dataStore.setOnboardingDone()
                                permissionLauncher.launch(needed.toTypedArray())
                            }
                        }
                        .show()
                }
            }
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
        // Private vault videos must never be surfaced in the system PiP window.
        if (app.playingVaultItem) return
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
        com.lnxv.player.widget.ContinueWidget.requestUpdate(applicationContext)
        if (!app.isPlayerInitialized) return
        val manager = app.playerManager
        if (isInPictureInPictureMode) {
            // PiP window was dismissed by the user: stop playback completely
            manager.stopPlayback()
        } else if (!backgroundPlayEnabled && !manager.audioOnly.value) {
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
        backgroundConnector.disconnect()
        val app = application as LNXVApplication
        if (isFinishing) {
            app.lockVault()
            if (app.isPlayerInitialized) app.playerManager.stopPlayback()
        }
        super.onDestroy()
    }
}
