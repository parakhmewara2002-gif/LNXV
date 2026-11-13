package com.lnxv.player.ui.navigation

import androidx.compose.runtime.Composable
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

@Composable
fun LNXVNavigationHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 } },
        exitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -it / 12 } },
        popEnterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 12 } },
        popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 8 } }
    ) {
        composable(Screen.Home.route) {
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

        composable(
            route = Screen.Player.route,
            arguments = listOf(navArgument("videoId") { type = NavType.LongType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getLong("videoId") ?: 0L
            val playerViewModel: PlayerViewModel = viewModel()
            com.lnxv.player.core.design.LNXVPlayerTheme {
                PlayerScreen(
                    videoId = videoId,
                    viewModel = playerViewModel,
                    onBackClick = { navController.popBackStack() }
                )
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
