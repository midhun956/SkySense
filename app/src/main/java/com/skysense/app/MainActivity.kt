package com.skysense.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.skysense.app.ui.navigation.Destination
import com.skysense.app.ui.navigation.NavGraph
import com.skysense.app.ui.theme.DimGrey
import com.skysense.app.ui.theme.GpsColor
import com.skysense.app.ui.theme.SkySenseTheme
import com.skysense.app.ui.theme.SpaceDeep
import kotlinx.coroutines.launch

data class BottomNavItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Destination.Dashboard, "Dashboard", Icons.Default.Dashboard),
    BottomNavItem(Destination.SkyMap, "Sky Map", Icons.Default.Satellite),
    BottomNavItem(Destination.Constellations, "Constellations", Icons.Default.Public),
    BottomNavItem(Destination.Learn, "Learn", Icons.Default.MenuBook),
    BottomNavItem(Destination.History, "History", Icons.Default.BarChart)
)

private val bottomNavRoutes = bottomNavItems.map { it.destination.route }.toSet()

class MainActivity : ComponentActivity() {

    private val app get() = application as SkySenseApplication

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            startGnssService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay.supportedModes.maxByOrNull { it.refreshRate }?.let { mode ->
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = mode.modeId
                }
            }
        }
        enableEdgeToEdge()
        checkAndRequestPermissions()

        setContent {
            SkySenseTheme {
                val navController = rememberNavController()
                val navBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStack?.destination?.route

                val showBottomBar = currentRoute in bottomNavRoutes

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = SpaceDeep,
                    bottomBar = {
                        AnimatedVisibility(
                            visible = showBottomBar,
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it }
                        ) {
                            NavigationBar(
                                containerColor = SpaceDeep,
                                tonalElevation = 0.dp
                            ) {
                                bottomNavItems.forEach { item ->
                                    val isSelected = navBackStack?.destination
                                        ?.hierarchy
                                        ?.any { it.route == item.destination.route } == true

                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                item.icon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        selected = isSelected,
                                        onClick = {
                                            navController.navigate(item.destination.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = GpsColor,
                                            selectedTextColor = GpsColor,
                                            unselectedIconColor = DimGrey,
                                            unselectedTextColor = DimGrey,
                                            indicatorColor = GpsColor.copy(alpha = 0.12f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    NavGraph(
                        navController = navController,
                        app = app
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasLocationPermission()) {
            startGnssService()
        }
        app.compassManager.start()
    }

    override fun onStop() {
        super.onStop()
        app.gnssRepository.stopGnss()
        app.gnssRepository.stopRecording()
        app.compassManager.stop()
    }

    private fun checkAndRequestPermissions() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startGnssService() {
        app.gnssRepository.startGnss()
        app.gnssRepository.startRecording(scope = lifecycleScope)
    }
}
