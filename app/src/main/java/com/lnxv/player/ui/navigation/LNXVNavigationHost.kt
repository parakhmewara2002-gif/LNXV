package com.lnxv.player.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lnxv.player.ui.home.HomeScreen
import com.lnxv.player.ui.home.HomeViewModel
import com.lnxv.player.ui.player.PlayerScreen
import com.lnxv.player.ui.player.PlayerViewModel
import com.lnxv.player.ui.settings.SettingsScreen
import com.lnxv.player.ui.settings.SettingsViewModel
import com.lnxv.player.ui.vault.VaultScreen
import com.lnxv.player.ui.vault.VaultViewModel

private const val IOS_NAV_MS = 300
private val IosNavEasing = FastOutSlowInEasing
private fun isPlayerEntry(entry: NavBackStackEntry?): Boolean = entry?.destination?.route == Screen.Player.route

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LNXVNavigationHost(navController: NavHostController) {
    // On vault lock, remove vault screens / vault playback from the back stack.
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.lnxv.player.LNXVApplication
    androidx.compose.runtime.LaunchedEffect(navController) {
        app.lockEvents.collect {
            val route = navController.currentDestination?.route
            if (route == Screen.Vault.route || route == Screen.Player.route) {
                navController.popBackStack(Screen.Home.route, inclusive = false)
            }
        }
    }
    // "Open with": navigate to the player when MainActivity accepted a VIEW intent.
    androidx.compose.runtime.LaunchedEffect(navController) {
        app.pendingOpenVideoId.collect { id ->
            if (id != null) {
                navController.navigate(Screen.Player.createRoute(id)) { launchSingleTop = true }
                app.pendingOpenVideoId.value = null
            }
        }
    }
    val reduce = com.lnxv.player.core.design.LocalReduceMotion.current ||
        com.lnxv.player.core.design.LocalReduceEffects.current
    // Interruptible springs instead of 380 ms tweens: if the user swipes back mid-transition the movement continues
    // from the current velocity instead of restarting. Reduce-motion collapses every transition to a snap.
    val slideSpec = if (reduce) androidx.compose.animation.core.snap<androidx.compose.ui.unit.IntOffset>()
        else androidx.compose.animation.core.spring(dampingRatio = 0.92f, stiffness = 420f)
    val fadeSpec = if (reduce) androidx.compose.animation.core.snap<Float>()
        else androidx.compose.animation.core.spring(dampingRatio = 0.92f, stiffness = 420f)
    SharedTransitionLayout {
    androidx.compose.runtime.CompositionLocalProvider(
        com.lnxv.player.core.design.LocalSharedTransitionScope provides this
    ) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        // iOS-style push/pop: new screen slides in from the right while the old one shifts ~30% left and dims;
        // back reverses. Routes to/from the player fade and scale instead.
        enterTransition = {
            when {
                reduce -> androidx.compose.animation.EnterTransition.None
                isPlayerEntry(targetState) ->
                    fadeIn(tween(180)) + scaleIn(fadeSpec, initialScale = 0.94f)
                else -> slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)
            }
        },
        exitTransition = {
            when {
                reduce -> androidx.compose.animation.ExitTransition.None
                isPlayerEntry(targetState) -> fadeOut(tween(140))
                else -> slideOutHorizontally(slideSpec) { -(it * 0.30f).toInt() } +
                    fadeOut(tween(200), targetAlpha = 0.65f)
            }
        },
        popEnterTransition = {
            when {
                reduce -> androidx.compose.animation.EnterTransition.None
                isPlayerEntry(initialState) -> fadeIn(tween(160))
                else -> slideInHorizontally(slideSpec) { -(it * 0.30f).toInt() } +
                    fadeIn(tween(200), initialAlpha = 0.65f)
            }
        },
        popExitTransition = {
            when {
                reduce -> androidx.compose.animation.ExitTransition.None
                isPlayerEntry(initialState) -> fadeOut(tween(140)) + scaleOut(fadeSpec, targetScale = 0.94f)
                else -> slideOutHorizontally(slideSpec) { it }
            }
        }
    ) {
        composable(Screen.Home.route) {
          androidx.compose.runtime.CompositionLocalProvider(
            com.lnxv.player.core.design.LocalNavAnimatedScope provides this
          ) {
            val homeViewModel: HomeViewModel = org.koin.androidx.compose.koinViewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onVideoClick = { video ->
                    navController.navigate(Screen.Player.createRoute(video.id))
                },
                onFolderClick = { folder ->
                    homeViewModel.openFolder(folder.path)
                },
                onVaultClick = {
                    navController.navigate(Screen.Vault.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onReceiveClick = {
                    navController.navigate(Screen.Receive.route)
                },
                onYouTubeClick = {
                    navController.navigate(Screen.YouTube.route)
                }
            )
          }
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(navArgument("videoId") { type = NavType.LongType })
        ) { backStackEntry ->
          androidx.compose.runtime.CompositionLocalProvider(
            com.lnxv.player.core.design.LocalNavAnimatedScope provides this
          ) {
            val videoId = backStackEntry.arguments?.getLong("videoId") ?: 0L
            val playerViewModel: PlayerViewModel = org.koin.androidx.compose.koinViewModel()
            val playerUi by playerViewModel.uiState.collectAsState()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                app.playerManager.playbackError.collect { msg ->
                    if (msg != null) {
                        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                        app.playerManager.clearPlaybackError()
                    }
                }
            }
            androidx.compose.runtime.LaunchedEffect(playerUi.accessDenied) {
                if (playerUi.accessDenied) navController.popBackStack()
            }
            // FLAG_SECURE while a vault item is open in the player (reference-counted together with the vault list)
            val isVaultItem = playerUi.currentVideo?.isVault == true
            com.lnxv.player.core.design.SecureScreen(enabled = isVaultItem)
            com.lnxv.player.core.design.LNXVPlayerTheme {
                PlayerScreen(
                    videoId = videoId,
                    viewModel = playerViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
          }
        }

        composable(Screen.Vault.route) {
            val vaultViewModel: VaultViewModel = org.koin.androidx.compose.koinViewModel()
            VaultScreen(
                viewModel = vaultViewModel,
                onBackClick = { navController.popBackStack() },
                onVideoClick = { video ->
                    navController.navigate(Screen.Player.createRoute(video.id))
                }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = org.koin.androidx.compose.koinViewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() },
                onOpenLicenses = { navController.navigate(Screen.Licenses.route) }
            )
        }

        composable(Screen.Receive.route) {
            com.lnxv.player.multiscreen.MultiScreenReceiverScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Licenses.route) {
            com.lnxv.player.ui.settings.LicensesScreen(onBackClick = { navController.popBackStack() })
        }

        composable(Screen.YouTube.route) {
            com.lnxv.player.ui.youtube.YouTubeScreen(
                session = app.session,
                onBack = { navController.popBackStack() }
            )
        }
    }
    }
    }
}
