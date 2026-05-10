package com.neurok.syncer.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neurok.syncer.ui.browser.BrowserScreen
import com.neurok.syncer.ui.detail.SongDetailScreen
import com.neurok.syncer.ui.home.HomeScreen
import com.neurok.syncer.ui.more.MoreScreen
import com.neurok.syncer.ui.preset.PresetScreen
import com.neurok.syncer.ui.settings.SettingsScreen

private val TOP_LEVEL_ROUTES = setOf("sync", "search", "preset", "more")

@Composable
fun AppNavigation(
    /** Called when the user wants to pick a folder. The callback receives the resulting URI. */
    onPickFolderFromActivity: (callback: (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            AnimatedVisibility(
                visible = currentRoute in TOP_LEVEL_ROUTES,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "sync",
                        onClick = { navController.navigateTab("sync") },
                        icon = { Icon(Icons.Filled.Sync, null) },
                        label = { Text("Sync") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "search",
                        onClick = { navController.navigateTab("search") },
                        icon = { Icon(Icons.Filled.Search, null) },
                        label = { Text("Search") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "preset",
                        onClick = { navController.navigateTab("preset") },
                        icon = { Icon(Icons.Filled.Tune, null) },
                        label = { Text("Preset") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "more",
                        onClick = { navController.navigateTab("more") },
                        icon = { Icon(Icons.Filled.MoreHoriz, null) },
                        label = { Text("More") },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "sync",
            modifier = Modifier.padding(padding),
        ) {
            composable("sync") {
                HomeScreen(
                    onNavigateToSettings = { navController.navigate("settings") },
                )
            }
            composable("search") {
                BrowserScreen(
                    onSongClick = { xxHash -> navController.navigate("detail/$xxHash") },
                )
            }
            composable("preset") {
                PresetScreen()
            }
            composable("more") {
                MoreScreen(
                    onNavigateToSettings = { navController.navigate("settings") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    onNavigateUp = { navController.popBackStack() },
                    onPickFolder = { callback -> onPickFolderFromActivity(callback) },
                )
            }
            composable(
                route = "detail/{xxHash}",
                arguments = listOf(navArgument("xxHash") { type = NavType.StringType }),
            ) { back ->
                val xxHash = back.arguments?.getString("xxHash") ?: return@composable
                SongDetailScreen(
                    xxHash = xxHash,
                    onNavigateUp = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

