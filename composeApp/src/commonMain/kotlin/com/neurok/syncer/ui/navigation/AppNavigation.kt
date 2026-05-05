package com.neurok.syncer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neurok.syncer.ui.browser.BrowserScreen
import com.neurok.syncer.ui.detail.SongDetailScreen
import com.neurok.syncer.ui.home.HomeScreen
import com.neurok.syncer.ui.settings.SettingsScreen

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Browser : Route("browser")
    data object Settings : Route("settings")
    data object SongDetail : Route("detail/{xxHash}") {
        fun withHash(xxHash: String) = "detail/$xxHash"
    }
}

@Composable
fun AppNavigation(
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.Home.path,
        modifier = modifier,
    ) {
        composable(Route.Home.path) {
            HomeScreen(
                onNavigateToBrowser = { navController.navigate(Route.Browser.path) },
                onNavigateToSettings = { navController.navigate(Route.Settings.path) },
            )
        }
        composable(Route.Browser.path) {
            BrowserScreen(
                onNavigateUp = { navController.popBackStack() },
                onSongClick = { xxHash -> navController.navigate(Route.SongDetail.withHash(xxHash)) },
            )
        }
        composable(Route.Settings.path) {
            SettingsScreen(
                onNavigateUp = { navController.popBackStack() },
                onPickFolder = onPickFolder,
            )
        }
        composable(
            route = Route.SongDetail.path,
            arguments = listOf(navArgument("xxHash") { type = NavType.StringType })
        ) { back ->
            val xxHash = back.arguments?.getString("xxHash") ?: return@composable
            SongDetailScreen(
                xxHash = xxHash,
                onNavigateUp = { navController.popBackStack() },
            )
        }
    }
}
