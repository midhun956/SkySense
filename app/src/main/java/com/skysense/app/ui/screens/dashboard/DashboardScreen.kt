package com.skysense.app.ui.screens.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skysense.app.data.model.ConstellationType
import com.skysense.app.data.model.GnssSnapshot
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.domain.SignalTier
import com.skysense.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: GnssRepository,
    onNavigateToAskAi: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Satellite,
                            contentDescription = null,
                            tint = CosmicBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "SkySense",
                            style = MaterialTheme.typography.titleLarge,
                            color = StarWhite
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToAskAi) {
                        Icon(Icons.Default.AutoAwesome, "Ask AI", tint = CosmicBlue)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = MoonGrey)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SpaceDeep
                )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── GPS Status indicator ──────────────────────────────────────────
            GpsStatusIndicator(isActive = state.isReceivingUpdates)

            // ── Main overview card ────────────────────────────────────────────
            OverviewCard(state = state)

            // ── Metrics grid ──────────────────────────────────────────────────
            MetricsGrid(snapshot = state.snapshot)

            // ── Constellation badges ──────────────────────────────────────────
            ConstellationBadges(constellations = state.snapshot.constellationsUsed)

            // ── DOP card ─────────────────────────────────────────────────────
            DopCard(snapshot = state.snapshot)

            Spacer(Modifier.height(80.dp)) // bottom nav clearance
        }
    }
}

@Composable
private fun GpsStatusIndicator(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(if (isActive) scale else 1f)
                .clip(CircleShape)
                .background(
                    if (isActive) SignalExcellent.copy(alpha = if (isActive) alpha else 0.3f)
                    else SignalPoor
                )
        )
        Text(
            text = if (isActive) "GPS Active — Receiving data" else "Searching for GPS signal…",
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) SignalExcellent else MoonGrey
        )
    }
}

@Composable
private fun OverviewCard(state: DashboardUiState) {
    val card = state.overviewCard
    val tierColor = when (card.signalTier) {
        SignalTier.EXCELLENT -> SignalExcellent
        SignalTier.GOOD -> SignalGood
        SignalTier.FAIR -> SignalFair
        SignalTier.POOR -> SignalPoor
        SignalTier.NONE -> DimGrey
    }
    val gradientBrush = Brush.verticalGradient(
        listOf(SpaceCardElevated, SpaceCard)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Signal tier dot
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(tierColor)
                    )
                    Text(
                        card.headline,
                        style = MaterialTheme.typography.titleLarge,
                        color = StarWhite
                    )
                }

                // Colored top border indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(listOf(tierColor, tierColor.copy(alpha = 0f)))
                        )
                )

                Text(
                    card.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoonGrey,
                    lineHeight = 22.sp
                )

                // Quick stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MiniStat("Accuracy", card.accuracyLabel, tierColor)
                    MiniStat("PDOP", card.pdopLabel, MoonGrey)
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DimGrey)
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MetricsGrid(snapshot: GnssSnapshot) {
    val timeFormat = remember { java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()) }
    val timeString = remember(snapshot.timestamp) { timeFormat.format(java.util.Date(snapshot.timestamp)) }
    
    val items = buildList {
        add(MetricItem(Icons.Default.Schedule, "Time", timeString))
        add(MetricItem(Icons.Default.MyLocation, "Latitude", "%.6f°".format(snapshot.latitude)))
        add(MetricItem(Icons.Default.MyLocation, "Longitude", "%.6f°".format(snapshot.longitude)))
        add(MetricItem(Icons.Default.Terrain, "Altitude", "${"%.1f".format(snapshot.altitude)} m"))
        add(MetricItem(Icons.Default.GpsFixed, "H. Accuracy", "${"%.1f".format(snapshot.horizontalAccuracy)} m"))
        add(MetricItem(Icons.Default.Height, "V. Accuracy", if (snapshot.verticalAccuracy > 0f) "${"%.1f".format(snapshot.verticalAccuracy)} m" else "—"))
        add(MetricItem(Icons.Default.Speed, "Speed", "${"%.1f".format(snapshot.speed * 3.6f)} km/h"))
        add(MetricItem(Icons.Default.Navigation, "Bearing", "${"%.0f".format(snapshot.bearing)}°"))
        add(MetricItem(Icons.Default.Satellite, "Sats Used", "${snapshot.satellitesUsed}"))
        add(MetricItem(Icons.Default.Visibility, "Sats Visible", "${snapshot.satellitesVisible}"))
        add(MetricItem(Icons.Default.Public, "Constellations", "${snapshot.constellationsUsed.filter { it != ConstellationType.UNKNOWN }.size}"))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Live Metrics",
            style = MaterialTheme.typography.titleSmall,
            color = DimGrey
        )
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { item ->
                    MetricChip(item, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

data class MetricItem(val icon: ImageVector, val label: String, val value: String)

@Composable
private fun MetricChip(item: MetricItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                item.icon, contentDescription = null,
                tint = CosmicBlue,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(item.label, style = MaterialTheme.typography.labelSmall, color = DimGrey)
                Text(
                    item.value,
                    style = MaterialTheme.typography.titleSmall,
                    color = StarWhite,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ConstellationBadges(constellations: Set<ConstellationType>) {
    val filtered = constellations.filter {
        it != ConstellationType.UNKNOWN && it != ConstellationType.SBAS
    }
    if (filtered.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Active Constellations", style = MaterialTheme.typography.titleSmall, color = DimGrey)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            filtered.forEach { constellation ->
                val color = constellation.color()
                Surface(
                    shape = RoundedCornerShape(50),
                    color = color.copy(alpha = 0.15f),
                    modifier = Modifier
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(
                            constellation.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DopCard(snapshot: GnssSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Dilution of Precision", style = MaterialTheme.typography.titleSmall, color = DimGrey)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DopIndicator("PDOP", snapshot.pdop)
                DopIndicator("HDOP", snapshot.hdop)
                DopIndicator("VDOP", snapshot.vdop)
            }
        }
    }
}

@Composable
private fun DopIndicator(label: String, value: Float) {
    val color = when {
        value <= 0f -> DimGrey
        value < 2f -> SignalExcellent
        value < 3f -> SignalGood
        value < 5f -> SignalFair
        else -> SignalPoor
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DimGrey)
        Text(
            if (value > 0f) "%.1f".format(value) else "—",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

fun ConstellationType.color(): Color = when (this) {
    ConstellationType.GPS -> GpsColor
    ConstellationType.GALILEO -> GalileoColor
    ConstellationType.GLONASS -> GlonassColor
    ConstellationType.BEIDOU -> BeiDouColor
    ConstellationType.QZSS -> QzssColor
    ConstellationType.IRNSS -> IrnssColor
    ConstellationType.SBAS -> SbasColor
    ConstellationType.UNKNOWN -> UnknownSatColor
}
