package com.skysense.app.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
// Vico charts removed temporarily due to API changes
import com.skysense.app.data.db.GnssHistoryEntity
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(repository: GnssRepository) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History & Analytics", color = StarWhite) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Time range selector
            TimeRangeSelector(
                selected = state.selectedRange,
                onSelect = viewModel::selectRange
            )

            // Summary stats
            SummaryStatsRow(state)

            // Accuracy chart
            if (state.entries.isNotEmpty()) {
                AccuracyChart(state.entries)
                SatelliteCountChart(state.entries)
            } else {
                EmptyHistoryCard()
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun TimeRangeSelector(selected: TimeRange, onSelect: (TimeRange) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeRange.values().forEach { range ->
            val isSelected = range == selected
            Surface(
                onClick = { onSelect(range) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) CosmicBlue.copy(alpha = 0.2f) else SpaceCard,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    range.label,
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth().wrapContentWidth(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) CosmicBlue else DimGrey,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SummaryStatsRow(state: HistoryUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatSummaryCard(
            label = "Avg Accuracy",
            value = if (state.avgAccuracy > 0f) "${"%.1f".format(state.avgAccuracy)}m" else "—",
            icon = Icons.Default.GpsFixed,
            color = CosmicBlue,
            modifier = Modifier.weight(1f)
        )
        StatSummaryCard(
            label = "Best Fix",
            value = if (state.bestAccuracy > 0f) "${"%.1f".format(state.bestAccuracy)}m" else "—",
            icon = Icons.Default.StarRate,
            color = SignalExcellent,
            modifier = Modifier.weight(1f)
        )
        StatSummaryCard(
            label = "Max Sats",
            value = if (state.maxSatellites > 0) "${state.maxSatellites}" else "—",
            icon = Icons.Default.Satellite,
            color = GalileoColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatSummaryCard(
    label: String, value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = DimGrey)
        }
    }
}

@Composable
private fun AccuracyChart(entries: List<GnssHistoryEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ShowChart, null, tint = CosmicBlue, modifier = Modifier.size(18.dp))
                Text("Accuracy Over Time (m) - Lower is Better", style = MaterialTheme.typography.titleSmall, color = StarWhite)
            }
            Spacer(Modifier.height(16.dp))
            if (entries.isEmpty()) return@Column

            val maxAccuracy = entries.maxOfOrNull { it.horizontalAccuracy }?.coerceAtLeast(10f) ?: 10f
            val textMeasurer = rememberTextMeasurer()

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Draw Grid & Labels
                val steps = 3
                val gridColor = DimGrey.copy(alpha = 0.3f)
                val labelStyle = TextStyle(color = DimGrey, fontSize = 10.sp)
                
                for (i in 0..steps) {
                    val ratio = i.toFloat() / steps
                    val y = height - ratio * height
                    val value = ratio * maxAccuracy
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    // Don't draw text for the very bottom edge so it doesn't get clipped
                    if (i > 0) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${"%.1f".format(value)}m",
                            topLeft = Offset(4.dp.toPx(), y),
                            style = labelStyle
                        )
                    }
                }

                val path = Path()
                val fillPath = Path()
                val stepX = width / (entries.size - 1).coerceAtLeast(1).toFloat()

                entries.forEachIndexed { index, entry ->
                    val x = index * stepX
                    val y = height - (entry.horizontalAccuracy / maxAccuracy) * height

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (index == entries.size - 1) {
                        fillPath.lineTo(x, height)
                        fillPath.close()
                    }
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(listOf(CosmicBlue.copy(alpha = 0.4f), CosmicBlue.copy(alpha = 0.0f)))
                )
                drawPath(
                    path = path, color = CosmicBlue,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
private fun SatelliteCountChart(entries: List<GnssHistoryEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Satellite, null, tint = GalileoColor, modifier = Modifier.size(18.dp))
                Text("Satellites Used", style = MaterialTheme.typography.titleSmall, color = StarWhite)
            }
            Spacer(Modifier.height(16.dp))
            if (entries.isEmpty()) return@Column

            val maxSats = entries.maxOfOrNull { it.satellitesUsed }?.coerceAtLeast(10)?.toFloat() ?: 10f
            val textMeasurer = rememberTextMeasurer()

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Draw Grid & Labels
                val steps = 3
                val gridColor = DimGrey.copy(alpha = 0.3f)
                val labelStyle = TextStyle(color = DimGrey, fontSize = 10.sp)
                
                for (i in 0..steps) {
                    val ratio = i.toFloat() / steps
                    val y = height - ratio * height
                    val value = (ratio * maxSats).toInt()
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    if (i > 0) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "$value",
                            topLeft = Offset(4.dp.toPx(), y),
                            style = labelStyle
                        )
                    }
                }

                val path = Path()
                val fillPath = Path()
                val stepX = width / (entries.size - 1).coerceAtLeast(1).toFloat()

                entries.forEachIndexed { index, entry ->
                    val x = index * stepX
                    val y = height - (entry.satellitesUsed / maxSats) * height

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (index == entries.size - 1) {
                        fillPath.lineTo(x, height)
                        fillPath.close()
                    }
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(listOf(GalileoColor.copy(alpha = 0.4f), GalileoColor.copy(alpha = 0.0f)))
                )
                drawPath(
                    path = path, color = GalileoColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.History, null, tint = DimGrey, modifier = Modifier.size(48.dp))
            Text("No history yet", style = MaterialTheme.typography.titleMedium, color = MoonGrey)
            Text(
                "Location history is automatically saved while the app is open and GPS is active.",
                style = MaterialTheme.typography.bodySmall,
                color = DimGrey
            )
        }
    }
}
