package com.skysense.app.ui.screens.satellite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.data.model.ConstellationType
import com.skysense.app.data.model.SatelliteInfo
import com.skysense.app.data.model.SignalQuality
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.domain.LocalInterpretationEngine
import com.skysense.app.ui.screens.dashboard.color
import com.skysense.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteDetailScreen(
    svid: Int,
    constellationName: String,
    band: String,
    repository: GnssRepository,
    onBack: () -> Unit
) {
    val satellites by repository.liveSatellites.collectAsStateWithLifecycle(initialValue = emptyList())
    val constellationType = runCatching { ConstellationType.valueOf(constellationName) }.getOrNull()
        ?: ConstellationType.UNKNOWN
    val satellite = satellites.find { it.svid == svid && it.constellation == constellationType && it.frequencyBandLabel == band }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (satellite != null) "${constellationType.displayName} PRN $svid"
                        else "Satellite Detail",
                        color = StarWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = MoonGrey)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceDeep)
            )
        },
        containerColor = SpaceBlack
    ) { padding ->
        if (satellite == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Satellite data unavailable.\nIt may have dropped below the horizon.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoonGrey
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SatelliteHeaderCard(satellite)
                SatelliteMetricsCard(satellite)
                FrequencyCard(satellite)
                EducationalCard(satellite)
                SignalStrengthBar(satellite)
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SatelliteHeaderCard(sat: SatelliteInfo) {
    val color = sat.constellation.color()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCardElevated)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(color))
                }
                Column {
                    Text(
                        "PRN ${sat.svid}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = StarWhite
                    )
                    Text(
                        sat.constellation.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = color
                    )
                }
                Spacer(Modifier.weight(1f))
                StatusBadge(sat.usedInFix)
            }

            HorizontalDivider(color = SpaceDivider)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalQualityChip(sat.signalQuality)
                if (sat.hasCarrierFrequency) {
                    FrequencyChip(sat.frequencyBandLabel)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(usedInFix: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (usedInFix) SignalExcellent.copy(alpha = 0.15f) else SpaceCard
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (usedInFix) SignalExcellent else DimGrey))
            Text(
                if (usedInFix) "In Fix" else "Tracking",
                style = MaterialTheme.typography.labelSmall,
                color = if (usedInFix) SignalExcellent else DimGrey
            )
        }
    }
}

@Composable
private fun SignalQualityChip(quality: SignalQuality) {
    val color = when (quality) {
        SignalQuality.EXCELLENT -> SignalExcellent
        SignalQuality.GOOD -> SignalGood
        SignalQuality.FAIR -> SignalFair
        SignalQuality.POOR -> SignalPoor
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(
            "Signal: ${quality.label}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun FrequencyChip(label: String) {
    Surface(shape = RoundedCornerShape(50), color = CosmicBlue.copy(alpha = 0.15f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = CosmicBlue
        )
    }
}

@Composable
private fun SatelliteMetricsCard(sat: SatelliteInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Orbital Parameters", style = MaterialTheme.typography.titleSmall, color = DimGrey)
            DetailRow(Icons.Default.ExpandLess, "Elevation", "${"%.1f".format(sat.elevationDegrees)}°",
                "Angle above the horizon. 90° = directly overhead.")
            DetailRow(Icons.Default.Explore, "Azimuth", "${"%.1f".format(sat.azimuthDegrees)}°",
                "Compass direction from North, clockwise.")
            DetailRow(Icons.Default.SignalCellularAlt, "C/N₀", "${"%.1f".format(sat.cn0DbHz)} dB-Hz",
                "Carrier-to-noise density. Higher = stronger signal.")
            DetailRow(Icons.Default.Satellite, "Satellite ID", "${sat.svid}",
                "The satellite's unique identifier within its constellation.")
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, hint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = CosmicBlue, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = DimGrey, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = StarWhite, fontWeight = FontWeight.SemiBold)
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = DimGrey.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 26.dp))
    }
}

@Composable
private fun FrequencyCard(sat: SatelliteInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Frequency Information", style = MaterialTheme.typography.titleSmall, color = DimGrey)
            if (sat.hasCarrierFrequency) {
                Text(
                    "Band: ${sat.frequencyBandLabel}  (${"%.3f".format(sat.carrierFrequencyHz / 1_000_000f)} MHz)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StarWhite
                )
                Text(
                    frequencyExplanation(sat.frequencyBandLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MoonGrey
                )
            } else {
                Text(
                    "Carrier frequency not available for this satellite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DimGrey
                )
            }
        }
    }
}

private fun frequencyExplanation(band: String): String = when {
    band.contains("L1") || band.contains("E1") || band.contains("B1") ->
        "L1 (1575.42 MHz) is the primary GPS civil signal. It is used by most smartphones and consumer devices."
    band.contains("L5") ->
        "L5 (1176.45 MHz) is a modern, higher-accuracy civil signal. Devices with L5 support can achieve sub-meter accuracy."
    band.contains("L2") ->
        "L2 (1227.60 MHz) is primarily a military and geodetic signal. Consumer access is increasing."
    else -> "This satellite is transmitting on a frequency used by its constellation for positioning."
}

@Composable
private fun EducationalCard(sat: SatelliteInfo) {
    val explanation = LocalInterpretationEngine.satelliteExplanation(
        sat.usedInFix, sat.cn0DbHz, sat.constellation.displayName
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCardElevated)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = GalileoColor, modifier = Modifier.size(18.dp))
                Text("What this means", style = MaterialTheme.typography.titleSmall, color = StarWhite)
            }
            Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MoonGrey)
        }
    }
}

@Composable
private fun SignalStrengthBar(sat: SatelliteInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Signal Strength", style = MaterialTheme.typography.titleSmall, color = DimGrey)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (sat.cn0DbHz / 50f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
                    color = when (sat.signalQuality) {
                        SignalQuality.EXCELLENT -> SignalExcellent
                        SignalQuality.GOOD -> SignalGood
                        SignalQuality.FAIR -> SignalFair
                        SignalQuality.POOR -> SignalPoor
                    },
                    trackColor = SpaceCardElevated
                )
                Text(
                    "${"%.1f".format(sat.cn0DbHz)} dB-Hz",
                    style = MaterialTheme.typography.labelMedium,
                    color = StarWhite,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = MaterialTheme.typography.labelSmall, color = DimGrey)
                Text("50 dB-Hz (max)", style = MaterialTheme.typography.labelSmall, color = DimGrey)
            }
        }
    }
}
