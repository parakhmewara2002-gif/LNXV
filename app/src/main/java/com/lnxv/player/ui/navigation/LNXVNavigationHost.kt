package com.lnxv.player.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
    val reduce = com.lnxv.player.core.design.LocalReduceEffects.current
    SharedTransitionLayout {
    androidx.compose.runtime.CompositionLocalProvider(
        com.lnxv.player.core.design.LocalSharedTransitionScope provides this
    ) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = { if (reduce) androidx.compose.animation.EnterTransition.None else fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 } },
        exitTransition = { if (reduce) androidx.compose.animation.ExitTransition.None else fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -it / 12 } },
        popEnterTransition = { if (reduce) androidx.compose.animation.EnterTransition.None else fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 12 } },
        popExitTransition = { if (reduce) androidx.compose.animation.ExitTransition.None else fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 8 } }
    ) {
        composable(Screen.Home.route) {
          androidx.compose.runtime.CompositionLocalProvider(
            com.lnxv.player.core.design.LocalNavAnimatedScope provides this
          ) {
            val homeViewModel: HomeViewModel = viewModel()
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
            // FLAG_SECURE while a vault item is open in the player
            val isVaultItem = playerUi.currentVideo?.isVault == true
            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
            androidx.compose.runtime.DisposableEffect(isVaultItem) {
                if (isVaultItem) activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                onDispose {
                    if (isVaultItem && !app.screenshotsBlockedGlobally) {
                        activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
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
            val vaultViewModel: VaultViewModel = viewModel()
            VaultScreen(
                viewModel = vaultViewModel,
                onBackClick = { navController.popBackStack() },
                onVideoClick = { video ->
                    navController.navigate(Screen.Player.createRoute(video.id))
                }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
    }
    }
}
