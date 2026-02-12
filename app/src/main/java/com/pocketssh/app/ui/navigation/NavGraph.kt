package com.pocketssh.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocketssh.app.ui.connections.ConnectionEditorScreen
import com.pocketssh.app.ui.connections.ConnectionListScreen
import com.pocketssh.app.ui.settings.KeyManagerScreen
import com.pocketssh.app.ui.settings.SettingsScreen
import com.pocketssh.app.ui.terminal.TerminalScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.ConnectionList.route,
    ) {
        composable(Screen.ConnectionList.route) {
            ConnectionListScreen(
                onNavigateToEditor = { profileId ->
                    navController.navigate(Screen.ConnectionEditor.createRoute(profileId))
                },
                onNavigateToTerminal = { profileId ->
                    navController.navigate(Screen.Terminal.createRoute(profileId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }

        composable(
            route = Screen.ConnectionEditor.route,
            arguments = listOf(
                navArgument("profileId") { type = NavType.LongType }
            ),
        ) {
            ConnectionEditorScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.Terminal.route,
            arguments = listOf(
                navArgument("profileId") { type = NavType.LongType }
            ),
        ) {
            TerminalScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToKeyManager = {
                    navController.navigate(Screen.KeyManager.route)
                },
            )
        }

        composable(Screen.KeyManager.route) {
            KeyManagerScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
