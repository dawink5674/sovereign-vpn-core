package com.sovereign.shield.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sovereign.shield.ui.screens.*

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Stats : Screen("stats")
    data object ThreatMap : Screen("threat_map")
    data object Log : Screen("log")
    data object Settings : Screen("settings")
}

@Composable
fun SovereignNavGraph(
    navController: NavHostController,
    onRequestVpnPermission: ((Intent, (Boolean) -> Unit) -> Unit)?
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onRequestVpnPermission = onRequestVpnPermission,
                onNavigateToStats = { navController.navigate(Screen.Stats.route) },
                onNavigateToMap = { navController.navigate(Screen.ThreatMap.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToLog = { navController.navigate(Screen.Log.route) }
            )
        }
        composable(Screen.Stats.route) { StatsScreen() }
        composable(Screen.ThreatMap.route) { ThreatMapScreen() }
        composable(Screen.Log.route) { LogScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}
