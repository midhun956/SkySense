package com.skysense.app.ui.navigation

/**
 * All navigation destinations in the app.
 */
sealed class Destination(val route: String) {
    // Bottom nav
    object Dashboard       : Destination("dashboard")
    object SkyMap          : Destination("skymap")
    object Constellations  : Destination("constellations")
    object Learn           : Destination("learn")
    object History         : Destination("history")

    // Side destinations
    object SatelliteDetail : Destination("satellite_detail/{svid}/{constellation}/{band}") {
        fun createRoute(svid: Int, constellation: String, band: String) = "satellite_detail/$svid/$constellation/$band"
    }
    object AskAi           : Destination("ask_ai")
    object Settings        : Destination("settings")
}

data class BottomNavItem(
    val destination: Destination,
    val label: String,
    val iconRes: String   // Material icon name (used with Icons.Default.*)
)
