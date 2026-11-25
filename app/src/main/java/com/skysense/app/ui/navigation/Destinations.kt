package com.skysense.app.ui.navigation

/**
 * All navigation destinations in the app.
 */
sealed class Destination(val route: String) {
    // Bottom nav
    object Dashboard       : Destination("dashboard")
    object SkyMap          : Destination("skymap")
    object Lab             : Destination("lab")
    object Learn           : Destination("learn")
    object History         : Destination("history")

    // Side & Lab destinations
    object Constellations      : Destination("constellations")
    object PrecisionClock      : Destination("lab/precision_clock")
    object HardwareInspector   : Destination("lab/hardware_inspector")
    object TelemetryExplorer   : Destination("lab/telemetry_explorer?svid={svid}&constellation={constellation}") {
        fun createRoute(svid: Int? = null, constellation: String? = null): String =
            if (svid != null && constellation != null) "lab/telemetry_explorer?svid=$svid&constellation=$constellation"
            else "lab/telemetry_explorer"
    }
    object RfObservatory       : Destination("lab/rf_observatory")
    object PositioningLab      : Destination("lab/positioning")
    object Altimeter           : Destination("lab/altimeter")
    object SatellitePrediction : Destination("lab/prediction")
    object Astronomy           : Destination("lab/astronomy")
    object DataRecorder        : Destination("lab/recorder")
    object ReceiverControls    : Destination("lab/controls")
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
