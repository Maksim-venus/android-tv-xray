package com.passwall.tv.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.passwall.tv.ui.home.HomeScreen
import com.passwall.tv.ui.settings.SettingsScreen

@Composable
fun PasswallNav(
    home: HomeUiState,
    settings: SettingsUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTest: () -> Unit,
    onSelectNode: (Long) -> Unit,
    onToggleInsecure: (Boolean) -> Unit,
    onToggleHttp: (Boolean) -> Unit,
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                state = home,
                onStart = onStart,
                onStop = onStop,
                onSettings = { nav.navigate("settings") },
                onTest = onTest,
            )
        }
        composable("settings") {
            SettingsScreen(
                state = settings,
                onBack = { nav.popBackStack() },
                onSelectNode = onSelectNode,
                onToggleInsecure = onToggleInsecure,
                onToggleHttp = onToggleHttp,
            )
        }
    }
}
