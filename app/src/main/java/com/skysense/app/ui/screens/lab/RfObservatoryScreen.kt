package com.skysense.app.ui.screens.lab

import android.location.GnssMeasurement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.ui.components.*
import com.skysense.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

@Composable
fun RfObservatoryScreen(
    repository: GnssRepository,
    onBack: () -> Unit
) {
    val satellites by repository.liveSatellites.collectAsStateWithLifecycle()
    val rawMeasurements by repository.rawMeasurements.collectAsStateWithLifecycle()
    val agcMeasurements by repository.agcMeasurements.collectAsStateWithLifecycle()
    val rawClock by repository.rawClock.collectAsStateWithLifecycle()

    val avgCn0History = remember { mutableStateListOf<Float>() }
    val waterfallRows = remember { mutableStateListOf<List<Float>>() } // each row = sorted C/N0s
    val rfEvents = remember { mutableStateListOf<String>() }
    var prevTrackedSet by remember { mutableStateOf<Set<String>>(emptySet()) }
    var prevDiscontinuity by remember { mutableIntStateOf(-1) }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    LaunchedEffect(satellites) {
        if (satellites.isEmpty()) return@LaunchedEffect
        val validCn0s = satellites.map { it.cn0DbHz }.filter { it > 0f }
        if (validCn0s.isNotEmpty()) {
            avgCn0History.add(validCn0s.average().toFloat())
            if (avgCn0History.size > 60) avgCn0History.removeAt(0)

            waterfallRows.add(validCn0s.sortedDescending().take(24))
            if (waterfallRows.size > 36) waterfallRows.removeAt(0)
        }

        val currentSet = satellites.map { "${it.constellation.shortName}-${it.svid} (${it.frequencyBandLabel})" }.toSet()
        if (prevTrackedSet.isNotEmpty()) {
            val acquired = currentSet - prevTrackedSet
            val lost = prevTrackedSet - currentSet
            val ts = timeFmt.format(Date())
            acquired.take(3).forEach { rfEvents.add(0, "[$ts] + ACQ $it") }
            lost.take(3).forEach { rfEvents.add(0, "[$ts] - LOST $it") }
            while (rfEvents.size > 25) rfEvents.removeAt(rfEvents.lastIndex)
        }
        prevTrackedSet = currentSet
    }

    LaunchedEffect(rawClock.hardwareClockDiscontinuityCount) {
        if (prevDiscontinuity >= 0 && rawClock.hardwareClockDiscontinuityCount > prevDiscontinuity) {
            rfEvents.add(0, "[${timeFmt.format(Date())}] ! HW Clock Discontinuity")
        }
        prevDiscontinuity = rawClock.hardwareClockDiscontinuityCount
    }

    // Group by frequency band
    val bandGroups = remember(satellites, rawMeasurements) {
        if (rawMeasurements.isNotEmpty()) {
            rawMeasurements.groupBy { it.frequencyBandLabel }
                .mapValues { (_, list) -> Pair(list.size, list.map { it.cn0DbHz }.average()) }
        } else {
            satellites.groupBy { it.frequencyBandLabel }
                .mapValues { (_, list) -> Pair(list.size, list.map { it.cn0DbHz.toDouble() }.average()) }
        }
    }

    // Dual-frequency pairs (same constellation + svid on multiple bands)
    val dualFreqPairs = remember(rawMeasurements, satellites) {
        val source = if (rawMeasurements.isNotEmpty()) {
            rawMeasurements.map { Triple("${it.constellation.shortName}-${it.svid}", it.frequencyBandLabel, it.cn0DbHz) }
        } else {
            satellites.map { Triple("${it.constellation.shortName}-${it.svid}", it.frequencyBandLabel, it.cn0DbHz.toDouble()) }
        }
        source.groupBy { it.first }
            .filter { (_, list) -> list.map { it.second }.distinct().size >= 2 }
    }

    // AGC summary
    val agcByBand = remember(agcMeasurements, rawMeasurements) {
        if (agcMeasurements.isNotEmpty()) {
            agcMeasurements.map { "${it.constellation.shortName} ${"%.1f".format(it.carrierFrequencyHz / 1e6)} MHz" to it.levelDb }
        } else {
            rawMeasurements.filter { it.hasAutomaticGainControlLevelDb }
                .groupBy { it.frequencyBandLabel }
                .map { (band, list) -> band to list.map { it.automaticGainControlLevelDb }.average() }
        }
    }

    // Experimental interference / multipath / spoofing indicators
    val multipathCount = rawMeasurements.count { it.multipathIndicator == GnssMeasurement.MULTIPATH_INDICATOR_DETECTED }
    val multipathPct = if (rawMeasurements.isNotEmpty()) (multipathCount * 100f / rawMeasurements.size) else 0f

    val cn0StdDev = remember(satellites) {
        val vals = satellites.map { it.cn0DbHz.toDouble() }.filter { it > 0.0 }
        if (vals.size < 4) null
        else {
            val mean = vals.average()
            sqrt(vals.map { (it - mean) * (it - mean) }.average())
        }
    }
    val uniformHighCn0Anomaly = cn0StdDev != null && cn0StdDev < 1.2 && (satellites.map { it.cn0DbHz }.average() > 44.0)

    LabInstrumentScaffold(
        title = "RF Observatory",
        subtitle = "Frequency bands, AGC & signal diagnostics",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val avgNow = satellites.map { it.cn0DbHz }.filter { it > 0f }.takeIf { it.isNotEmpty() }?.average()
            PlainSummaryBanner(
                headline = if (uniformHighCn0Anomaly) "Signal Anomaly Detected" else if (multipathPct > 25f) "High Signal Reflections (Multipath)" else "RF Environment Looks Healthy",
                description = "Monitors the radio frequencies your phone hears from space. Stronger C/N₀ (30+ dB-Hz) means cleaner satellite signals.",
                accentColor = if (uniformHighCn0Anomaly) SignalPoor else if (multipathPct > 25f) SignalFair else SignalExcellent
            )
            QuickStatRow(
                listOf(
                    Triple("Avg Signal", avgNow?.let { "${"%.1f".format(it)} dB" } ?: "—", SignalExcellent),
                    Triple("Bands", "${bandGroups.size}", CosmicBlueLight),
                    Triple("Dual-Band", "${dualFreqPairs.size} SVs", GalileoColor)
                )
            )

            // ── Frequency Band Breakdown ─────────────────────────────────────
            LabSectionCard("Observed Frequency Bands", origin = DataOrigin.HARDWARE) {
                if (bandGroups.isEmpty()) {
                    Text("No band telemetry observed yet.", style = MaterialTheme.typography.bodySmall, color = DimGrey)
                } else {
                    bandGroups.entries.sortedByDescending { it.value.first }.forEach { (band, stats) ->
                        val (count, avgCn0) = stats
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MonoMetricRow(
                                "$band ($count SV)",
                                "${"%.1f".format(avgCn0)} dB-Hz",
                                valueColor = if (avgCn0 >= 35.0) SignalExcellent else if (avgCn0 >= 25.0) SignalGood else SignalFair
                            )
                            LinearProgressIndicator(
                                progress = { (avgCn0.toFloat() / 50f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(5.dp),
                                color = if (band.contains("5") || band.contains("2")) GalileoColor else CosmicBlue,
                                trackColor = SpaceDark
                            )
                        }
                    }
                }
            }

            // ── Real-time Mean C/N0 Graph ────────────────────────────────────
            LabSectionCard("Mean C/N₀ Trend (dB-Hz)", origin = DataOrigin.HARDWARE) {
                LabLineChart(avgCn0History, SignalExcellent, "dB-Hz")
            }

            // ── Historical Signal Waterfall ──────────────────────────────────
            LabSectionCard("Signal Waterfall (Time × Channels)", origin = DataOrigin.HARDWARE) {
                if (waterfallRows.isEmpty()) {
                    Text("Collecting waterfall frames…", style = MaterialTheme.typography.bodySmall, color = DimGrey)
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(SpaceDark, RoundedCornerShape(10.dp))
                            .padding(6.dp)
                    ) {
                        val rows = waterfallRows.size
                        val cols = 24
                        val cellW = size.width / cols
                        val cellH = size.height / rows.coerceAtLeast(1)
                        waterfallRows.forEachIndexed { r, row ->
                            row.forEachIndexed { c, cn0 ->
                                val color = when {
                                    cn0 >= 40f -> SignalExcellent
                                    cn0 >= 30f -> SignalGood
                                    cn0 >= 20f -> SignalFair
                                    else -> SignalPoor
                                }.copy(alpha = (cn0 / 50f).coerceIn(0.25f, 1f))
                                drawRect(
                                    color = color,
                                    topLeft = Offset(c * cellW, r * cellH),
                                    size = Size(cellW - 1f, cellH - 1f)
                                )
                            }
                        }
                    }
                    Text(
                        "Columns sorted by strongest C/N₀; newest epochs at bottom.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DimGrey
                    )
                }
            }

            // ── AGC Monitor ──────────────────────────────────────────────────
            LabSectionCard("Automatic Gain Control (AGC)", origin = DataOrigin.HARDWARE) {
                if (agcByBand.isEmpty()) {
                    Text(
                        "AGC level is not reported by this receiver or requires Android 13+ GnssAutomaticGainControls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DimGrey
                    )
                } else {
                    agcByBand.forEach { (label, level) ->
                        MonoMetricRow(label, "${"%.2f".format(level)} dB")
                    }
                }
            }

            // ── Dual-Frequency Comparison ────────────────────────────────────
            LabSectionCard("Dual-Frequency Comparison (${dualFreqPairs.size} SVs)", origin = DataOrigin.DERIVED) {
                if (dualFreqPairs.isEmpty()) {
                    Text(
                        "No satellites simultaneously tracked on two bands right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DimGrey
                    )
                } else {
                    dualFreqPairs.entries.take(8).forEach { (sv, list) ->
                        val summary = list.distinctBy { it.second }
                            .joinToString(" | ") { "${it.second}: ${"%.1f".format(it.third)}" }
                        MonoMetricRow(sv, summary)
                    }
                }
            }

            // ── Experimental RF Anomaly Indicators ───────────────────────────
            LabSectionCard("RF Anomaly Heuristics", origin = DataOrigin.EXPERIMENTAL) {
                MonoMetricRow(
                    "Multipath Flagged",
                    if (rawMeasurements.isEmpty()) "No raw data" else "$multipathCount/${rawMeasurements.size} (${"%.0f".format(multipathPct)}%)",
                    valueColor = if (multipathPct > 25f) SignalFair else SignalExcellent
                )
                MonoMetricRow(
                    "C/N₀ Uniformity Check",
                    if (cn0StdDev == null) "Insufficient SVs"
                    else if (uniformHighCn0Anomaly) "Anomalous (σ=${"%.2f".format(cn0StdDev)})"
                    else "Normal (σ=${"%.2f".format(cn0StdDev)} dB)",
                    valueColor = if (uniformHighCn0Anomaly) SignalPoor else SignalExcellent
                )
                Text(
                    "Note: Smartphones are not SDRs. Anomaly heuristics are experimental indicators, not definitive jamming or spoofing detections.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DimGrey
                )
            }

            // ── Acquisition & Loss Log ───────────────────────────────────────
            LabSectionCard("Acquisition & Loss Events", origin = DataOrigin.DERIVED) {
                if (rfEvents.isEmpty()) {
                    Text("Listening for satellite acquisition/loss transitions…", style = MaterialTheme.typography.bodySmall, color = DimGrey)
                } else {
                    rfEvents.take(10).forEach { ev ->
                        Text(
                            ev,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (ev.contains("+ ACQ")) SignalExcellent else if (ev.contains("!")) SignalFair else MoonGrey
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
