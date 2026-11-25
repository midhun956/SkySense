package com.skysense.app.ui.screens.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.domain.GnssMath
import com.skysense.app.domain.KalmanFilter2D
import com.skysense.app.ui.components.*
import com.skysense.app.ui.theme.*
import com.skysense.app.util.performHapticConfirm
import com.skysense.app.util.performHapticToggleOff
import com.skysense.app.util.performHapticToggleOn
import kotlin.math.max
import kotlin.math.sqrt

@Composable
fun PositioningLabScreen(
    repository: GnssRepository,
    onBack: () -> Unit
) {
    val snapshot by repository.liveSnapshot.collectAsStateWithLifecycle()
    val satellites by repository.liveSatellites.collectAsStateWithLifecycle()
    val rawMeasurements by repository.rawMeasurements.collectAsStateWithLifecycle()
    val view = LocalView.current

    var kalmanEnabled by remember { mutableStateOf(true) }
    val kalman = remember { KalmanFilter2D() }

    val rawFixes = remember { mutableStateListOf<Triple<Double, Double, Double>>() } // lat, lon, alt
    val filteredFixes = remember { mutableStateListOf<Pair<Double, Double>>() }
    val hAccHistory = remember { mutableStateListOf<Float>() }
    val vAccHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(snapshot.timestamp, snapshot.hasValidFix) {
        if (!snapshot.hasValidFix) return@LaunchedEffect
        rawFixes.add(Triple(snapshot.latitude, snapshot.longitude, snapshot.altitude))
        if (rawFixes.size > 120) rawFixes.removeAt(0)

        val filtered = kalman.update(snapshot.latitude, snapshot.longitude, snapshot.horizontalAccuracy, snapshot.timestamp)
        filteredFixes.add(filtered)
        if (filteredFixes.size > 120) filteredFixes.removeAt(0)

        hAccHistory.add(snapshot.horizontalAccuracy)
        if (hAccHistory.size > 60) hAccHistory.removeAt(0)
        if (snapshot.verticalAccuracy > 0f) {
            vAccHistory.add(snapshot.verticalAccuracy)
            if (vAccHistory.size > 60) vAccHistory.removeAt(0)
        }
    }

    val meanLat = if (rawFixes.isNotEmpty()) rawFixes.map { it.first }.average() else snapshot.latitude
    val meanLon = if (rawFixes.isNotEmpty()) rawFixes.map { it.second }.average() else snapshot.longitude
    val meanAlt = if (rawFixes.isNotEmpty()) rawFixes.map { it.third }.average() else snapshot.altitude

    val offsets = remember(rawFixes.size, meanLat, meanLon) {
        rawFixes.map { (lat, lon, _) -> GnssMath.enuOffsetMeters(meanLat, meanLon, lat, lon) }
    }
    val (cep50, cep95) = remember(offsets) { GnssMath.computeCep(offsets) }
    val maxSpread = offsets.maxOfOrNull { (e, n) -> sqrt(e * e + n * n) } ?: 0.0

    val trueDop = remember(satellites) {
        val used = satellites.filter { it.usedInFix }
        GnssMath.computeTrueDop(if (used.size >= 4) used else satellites)
    }

    val latestFiltered = filteredFixes.lastOrNull()
    val hasSatPvt = rawMeasurements.any { it.satellitePvt != null }
    val hasValidPr = rawMeasurements.count { GnssMath.computePseudorangeMeters(it, repository.rawClock.value) != null }

    LabInstrumentScaffold(
        title = "Positioning Lab",
        subtitle = "Dispersion, averaging, Kalman & DOP",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                rawFixes.clear()
                filteredFixes.clear()
                hAccHistory.clear()
                vAccHistory.clear()
                kalman.reset()
                view.performHapticConfirm()
            }) {
                Icon(Icons.Default.RestartAlt, "Reset", tint = CosmicBlue)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlainSummaryBanner(
                headline = if (rawFixes.size < 2) "Collecting Position Samples…"
                else "50% of fixes fall within ${"%.1f".format(cep50)} m",
                description = "Stand still to measure how steady your GPS fix is over time. Toggle Kalman Smoothing to compare raw vs. smoothed coordinates.",
                accentColor = SignalFair
            )
            QuickStatRow(
                listOf(
                    Triple("CEP50", if (offsets.isEmpty()) "—" else "${"%.2f".format(cep50)} m", SignalExcellent),
                    Triple("CEP95", if (offsets.isEmpty()) "—" else "${"%.2f".format(cep95)} m", SignalFair),
                    Triple("PDOP", if (trueDop.isValid) "${"%.2f".format(trueDop.pdop)}" else "—", CosmicBlueLight)
                )
            )

            // ── Dispersion Scatter Plot ──────────────────────────────────────
            LabSectionCard("Live Position Dispersion (ENU)", origin = DataOrigin.DERIVED) {
                if (offsets.size < 2) {
                    Text("Collecting GNSS fixes for scatter plot…", style = MaterialTheme.typography.bodySmall, color = DimGrey)
                } else {
                    val scaleMeters = max(5.0, max(cep95 * 1.4, maxSpread * 1.1))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(SpaceDark, RoundedCornerShape(14.dp))
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val maxR = size.minDimension / 2f - 16f
                        val pxPerMeter = (maxR / scaleMeters).toFloat()

                        // Crosshairs
                        drawLine(SpaceDivider, Offset(cx, 10f), Offset(cx, size.height - 10f), 1.5f)
                        drawLine(SpaceDivider, Offset(10f, cy), Offset(size.width - 10f, cy), 1.5f)

                        // CEP50 & CEP95 rings
                        drawCircle(
                            color = SignalExcellent.copy(alpha = 0.5f),
                            radius = (cep50.toFloat() * pxPerMeter).coerceAtMost(maxR),
                            center = Offset(cx, cy),
                            style = Stroke(width = 2f)
                        )
                        drawCircle(
                            color = SignalFair.copy(alpha = 0.5f),
                            radius = (cep95.toFloat() * pxPerMeter).coerceAtMost(maxR),
                            center = Offset(cx, cy),
                            style = Stroke(width = 2f)
                        )

                        // Scatter points
                        offsets.forEachIndexed { idx, (e, n) ->
                            val x = cx + e.toFloat() * pxPerMeter
                            val y = cy - n.toFloat() * pxPerMeter
                            val isLatest = idx == offsets.lastIndex
                            drawCircle(
                                color = if (isLatest) CosmicBlue else MoonGrey.copy(alpha = 0.55f),
                                radius = if (isLatest) 7f else 4f,
                                center = Offset(x, y)
                            )
                        }

                        // Filtered point
                        if (kalmanEnabled && latestFiltered != null) {
                            val (fe, fn) = GnssMath.enuOffsetMeters(meanLat, meanLon, latestFiltered.first, latestFiltered.second)
                            drawCircle(
                                color = SignalExcellent,
                                radius = 7f,
                                center = Offset(cx + fe.toFloat() * pxPerMeter, cy - fn.toFloat() * pxPerMeter)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Samples: ${rawFixes.size}", style = MaterialTheme.typography.labelSmall, color = DimGrey)
                        Text("CEP50: ${"%.2f".format(cep50)} m", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = SignalExcellent)
                        Text("CEP95: ${"%.2f".format(cep95)} m", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = SignalFair)
                    }
                    Text(
                        "Note: CEP50/CEP95 reflect fix precision (stability around mean), not absolute ground-truth accuracy.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DimGrey
                    )
                }
            }

            // ── Stationary Averaging & Kalman Comparison ─────────────────────
            LabSectionCard("Averaging & Kalman Filter", origin = DataOrigin.EXPERIMENTAL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2D Kalman Smoothing", style = MaterialTheme.typography.bodyMedium, color = StarWhite)
                    Switch(
                        checked = kalmanEnabled,
                        onCheckedChange = {
                            kalmanEnabled = it
                            if (it) view.performHapticToggleOn() else view.performHapticToggleOff()
                        }
                    )
                }
                MonoMetricRow("Averaged Lat / Lon", "${"%.6f".format(meanLat)}, ${"%.6f".format(meanLon)}")
                MonoMetricRow("Averaged Altitude", "${"%.2f".format(meanAlt)} m")
                MonoMetricRow("Standard Fix", "${"%.6f".format(snapshot.latitude)}, ${"%.6f".format(snapshot.longitude)}")
                if (kalmanEnabled && latestFiltered != null) {
                    MonoMetricRow(
                        "Kalman Filtered Fix",
                        "${"%.6f".format(latestFiltered.first)}, ${"%.6f".format(latestFiltered.second)}",
                        valueColor = SignalExcellent
                    )
                }
                MonoMetricRow("Max Radial Spread", "${"%.2f".format(maxSpread)} m")
            }

            // ── True 4x4 Matrix DOP ──────────────────────────────────────────
            LabSectionCard("Satellite Geometry & DOP", origin = DataOrigin.DERIVED) {
                if (!trueDop.isValid) {
                    Text("Need ≥4 well-spaced satellites to invert (HᵀH).", style = MaterialTheme.typography.bodySmall, color = DimGrey)
                } else {
                    MonoMetricRow("GDOP (Geometric)", "${"%.2f".format(trueDop.gdop)}")
                    MonoMetricRow("PDOP (3D Position)", "${"%.2f".format(trueDop.pdop)}")
                    MonoMetricRow("HDOP (Horizontal)", "${"%.2f".format(trueDop.hdop)}")
                    MonoMetricRow("VDOP (Vertical)", "${"%.2f".format(trueDop.vdop)}")
                    MonoMetricRow("TDOP (Time)", "${"%.2f".format(trueDop.tdop)}")
                }
            }

            // ── Accuracy History ─────────────────────────────────────────────
            LabSectionCard("Horizontal Accuracy History (m)", origin = DataOrigin.HARDWARE) {
                LabLineChart(hAccHistory, CosmicBlue, "m")
            }
            if (vAccHistory.isNotEmpty()) {
                LabSectionCard("Vertical Accuracy History (m)", origin = DataOrigin.HARDWARE) {
                    LabLineChart(vAccHistory, GalileoColor, "m")
                }
            }

            // ── Raw Pseudorange / WLS / PPP / RTK Feasibility ────────────────
            LabSectionCard("Advanced Solver Feasibility", origin = DataOrigin.EXPERIMENTAL) {
                MonoMetricRow("Valid Pseudoranges", "$hasValidPr channels")
                MonoMetricRow("Hardware Satellite PVT", if (hasSatPvt) "Available (WLS feasible)" else "Unavailable (needs ephemeris)")
                MonoMetricRow("Carrier-Phase (ADR)", if (rawMeasurements.any { (it.accumulatedDeltaRangeState and 1) != 0 }) "Observed" else "Not Observed")
                Text(
                    "Android raw pseudoranges require satellite ECEF positions (via API 35+ SatellitePvt or external broadcast ephemeris/RTCM) to compute an independent WLS/PPP/RTK fix without fabricating orbits.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DimGrey
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
