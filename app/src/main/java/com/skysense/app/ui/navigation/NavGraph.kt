package com.skysense.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import com.skysense.app.SkySenseApplication
import com.skysense.app.ui.screens.askai.AskAiScreen
import com.skysense.app.ui.screens.constellation.ConstellationExplorerScreen
import com.skysense.app.ui.screens.dashboard.DashboardScreen
import com.skysense.app.ui.screens.history.HistoryScreen
import com.skysense.app.ui.screens.learn.LearnScreen
import com.skysense.app.ui.screens.satellite.SatelliteDetailScreen
import com.skysense.app.ui.screens.settings.SettingsScreen
import com.skysense.app.ui.screens.skymap.SkyMapScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    app: SkySenseApplication
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Dashboard.route,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) +
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(200)) +
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300)) +
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(200)) +
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200))
        }
    ) {
        composable(Destination.Dashboard.route) {
            DashboardScreen(
                repository = app.gnssRepository,
                environmentRepository = app.environmentRepository,
                onNavigateToAskAi = { navController.navigate(Destination.AskAi.route) },
                onNavigateToSettings = { navController.navigate(Destination.Settings.route) }
            )
        }
        composable(Destination.SkyMap.route) {
            SkyMapScreen(
                repository = app.gnssRepository,
                compassManager = app.compassManager,
                onSatelliteClick = { svid, constellation, band ->
                    navController.navigate(Destination.SatelliteDetail.createRoute(svid, constellation, band))
                }
            )
        }
        composable(Destination.Constellations.route) {
            ConstellationExplorerScreen()
        }
        composable(Destination.Learn.route) {
            LearnScreen()
        }
        composable(Destination.History.route) {
            HistoryScreen(repository = app.gnssRepository)
        }
        composable(
            route = Destination.SatelliteDetail.route,
            arguments = listOf(
                navArgument("svid") { type = NavType.IntType },
                navArgument("constellation") { type = NavType.StringType },
                navArgument("band") { type = NavType.StringType }
            )
        ) { backStack ->
            val svid = backStack.arguments?.getInt("svid") ?: 0
            val constellation = backStack.arguments?.getString("constellation") ?: ""
            val band = backStack.arguments?.getString("band") ?: ""
            SatelliteDetailScreen(
                svid = svid,
                constellationName = constellation,
                band = band,
                repository = app.gnssRepository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destination.AskAi.route) {
            AskAiScreen(
                repository = app.gnssRepository,
                environmentRepository = app.environmentRepository,
                prefsManager = app.prefsManager,
                geminiClient = app.geminiClient,
                networkMonitor = app.networkMonitor,
                onNavigateToSettings = { navController.navigate(Destination.Settings.route) }
            )
        }
        composable(Destination.Settings.route) {
            val scope = rememberCoroutineScope()
            SettingsScreen(
                prefsManager = app.prefsManager,
                onClearHistory = { scope.launch { app.gnssRepository.clearAllHistory() } },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
