package com.skysense.app.ui.screens.lab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skysense.app.ui.navigation.Destination
import com.skysense.app.ui.theme.*
import com.skysense.app.util.performHapticSegmentTick

private data class LabInstrumentCardData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val route: String
)

private val labInstruments = listOf(
    LabInstrumentCardData(
        title = "Precision Clock",
        subtitle = "Satellite-derived time",
        icon = Icons.Default.AccessTime,
        accent = CosmicBlue,
        route = Destination.PrecisionClock.route
    ),
    LabInstrumentCardData(
        title = "RF Observatory",
        subtitle = "Signal diagnostics",
        icon = Icons.Default.CellTower,
        accent = SignalExcellent,
        route = Destination.RfObservatory.route
    ),
    LabInstrumentCardData(
        title = "Telemetry",
        subtitle = "Raw satellite data",
        icon = Icons.Default.SatelliteAlt,
        accent = QzssColor,
        route = Destination.TelemetryExplorer.route
    ),
    LabInstrumentCardData(
        title = "Positioning Lab",
        subtitle = "Experimental fixes",
        icon = Icons.Default.GpsFixed,
        accent = SignalFair,
        route = Destination.PositioningLab.route
    ),
    LabInstrumentCardData(
        title = "Hardware",
        subtitle = "Receiver capabilities",
        icon = Icons.Default.Memory,
        accent = Color(0xFF00BCD4),
        route = Destination.HardwareInspector.route
    ),
    LabInstrumentCardData(
        title = "Altimeter",
        subtitle = "Vertical positioning",
        icon = Icons.Default.Terrain,
        accent = GalileoColor,
        route = Destination.Altimeter.route
    ),
    LabInstrumentCardData(
        title = "Astronomy",
        subtitle = "Celestial calculations",
        icon = Icons.Default.WbSunny,
        accent = Color(0xFFFFD54F),
        route = Destination.Astronomy.route
    ),
    LabInstrumentCardData(
        title = "Data Recorder",
        subtitle = "Raw GNSS export",
        icon = Icons.Default.Storage,
        accent = IrnssColor,
        route = Destination.DataRecorder.route
    ),
    LabInstrumentCardData(
        title = "Sat Prediction",
        subtitle = "Orbital visibility forecast",
        icon = Icons.Default.Public,
        accent = CosmicBlueLight,
        route = Destination.SatellitePrediction.route
    ),
    LabInstrumentCardData(
        title = "Receiver Controls",
        subtitle = "Tracking & A-GNSS",
        icon = Icons.Default.Tune,
        accent = SignalGood,
        route = Destination.ReceiverControls.route
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(
    onNavigateToInstrument: (String) -> Unit
) {
    val view = LocalView.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Science,
                            contentDescription = null,
                            tint = CosmicBlue,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            "SkySense Lab",
                            style = MaterialTheme.typography.titleLarge,
                            color = StarWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceDeep)
            )
        },
        containerColor = SpaceBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Explore the science behind your position.",
                style = MaterialTheme.typography.bodyMedium,
                color = MoonGrey
            )

            val rows = labInstruments.chunked(2)
            rows.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { item ->
                        LabInstrumentCard(
                            data = item,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                view.performHapticSegmentTick()
                                onNavigateToInstrument(item.route)
                            }
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(84.dp)) // Bottom nav clearance
        }
    }
}

@Composable
private fun LabInstrumentCard(
    data: LabInstrumentCardData,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = data.icon,
                contentDescription = data.title,
                tint = data.accent,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = StarWhite,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = data.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MoonGrey
                )
            }
        }
    }
}
