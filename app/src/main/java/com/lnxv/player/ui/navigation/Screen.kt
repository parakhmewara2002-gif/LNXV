package com.lnxv.player.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Vault : Screen("vault")
    object Settings : Screen("settings")

    object Player : Screen("player/{videoId}") {
        fun createRoute(videoId: Long): String = "player/$videoId"
    }

    object FolderDetail : Screen("folder/{folderPath}") {
        fun createRoute(folderPath: String): String = "folder/${java.net.URLEncoder.encode(folderPath, "UTF-8")}"
    }
}
