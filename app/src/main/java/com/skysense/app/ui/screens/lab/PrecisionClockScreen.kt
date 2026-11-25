package com.skysense.app.ui.screens.lab

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.domain.ClockSyncStatus
import com.skysense.app.domain.PrecisionClockEngine
import com.skysense.app.ui.components.*
import com.skysense.app.ui.theme.*
import com.skysense.app.util.performHapticSegmentTick
import kotlinx.coroutines.delay

@Composable
fun PrecisionClockScreen(
    repository: GnssRepository,
    onBack: () -> Unit
) {
    val rawClock by repository.rawClock.collectAsStateWithLifecycle()
    val view = androidx.compose.ui.platform.LocalView.current
    var use24Hour by remember { mutableStateOf(false) }

    // Calm 4Hz monotonic check so seconds flip crisply without screen jitter
    var nowElapsedNanos by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    var nowSystemMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowElapsedNanos = SystemClock.elapsedRealtimeNanos()
            nowSystemMs = System.currentTimeMillis()
            delay(250L)
        }
    }

    val clockState = remember(rawClock, nowElapsedNanos, use24Hour) {
        PrecisionClockEngine.compute(rawClock, nowElapsedNanos, nowSystemMs, use24HourFormat = use24Hour)
    }

    val driftHistory = remember { mutableStateListOf<Float>() }
    val offsetHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(rawClock.receivedElapsedRealtimeNanos) {
        if (rawClock.isValid && rawClock.hasFullBiasNanos) {
            if (rawClock.hasDriftNanosPerSecond) {
                driftHistory.add(rawClock.driftNanosPerSecond.toFloat())
                if (driftHistory.size > 60) driftHistory.removeAt(0)
            }
            val snapState = PrecisionClockEngine.compute(
                rawClock,
                rawClock.receivedElapsedRealtimeNanos,
                rawClock.receivedSystemTimeMs
            )
            offsetHistory.add(snapState.gnssMinusSystemMs.toFloat())
            if (offsetHistory.size > 60) offsetHistory.removeAt(0)
        }
    }

    val statusColor = when (clockState.syncStatus) {
        ClockSyncStatus.SYNCED -> SignalExcellent
        ClockSyncStatus.INTERPOLATING -> CosmicBlue
        ClockSyncStatus.STALE -> SignalFair
        ClockSyncStatus.SIGNAL_LOST, ClockSyncStatus.UNAVAILABLE -> SignalPoor
    }

    LabInstrumentScaffold(
        title = "Precision Clock",
        subtitle = "Satellite-derived atomic timekeeping",
        onBack = onBack,
        actions = {
            FilterChip(
                selected = use24Hour,
                onClick = {
                    view.performHapticSegmentTick()
                    use24Hour = !use24Hour
                },
                label = {
                    Text(
                        if (use24Hour) "24H" else "12H",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                },
                modifier = Modifier.padding(end = 8.dp)
            )
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
            // ── Primary Scientific Digital Readout ───────────────────────────
            PlainSummaryBanner(
                headline = if (clockState.syncStatus == ClockSyncStatus.UNAVAILABLE)
                    "Waiting for Satellite Time Lock" else "Synced to GNSS Atomic Clocks",
                description = "This clock is derived directly from orbiting GNSS satellites—no internet or phone wall-clock time is used.",
                accentColor = statusColor
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SpaceCardElevated)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                clockState.syncStatus.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor
                            )
                        }
                        OriginBadge(
                            if (clockState.syncStatus == ClockSyncStatus.SYNCED)
                                DataOrigin.HARDWARE else DataOrigin.DERIVED
                        )
                    }

                    Text(
                        text = "LOCAL TIME  (${java.util.TimeZone.getDefault().getDisplayName(false, java.util.TimeZone.SHORT)})",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MoonGrey
                    )

                    Text(
                        text = clockState.localTimeText,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 38.sp,
                            letterSpacing = 0.5.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = StarWhite,
                        maxLines = 1
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("UTC (${clockState.utcDateText})", style = MaterialTheme.typography.labelSmall, color = DimGrey)
                            Text(
                                clockState.utcTimeText,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = CosmicBlueLight
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("EST. ACCURACY (1σ)", style = MaterialTheme.typography.labelSmall, color = DimGrey)
                            Text(
                                if (clockState.biasUncertaintyNanos > 0) "±${formatNanos(clockState.biasUncertaintyNanos)}"
                                else "Unknown",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                color = SignalExcellent
                            )
                        }
                    }

                    if (!clockState.isLeapSecondKnown && clockState.syncStatus != ClockSyncStatus.UNAVAILABLE) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SignalFair.copy(alpha = 0.14f)
                        ) {
                            Text(
                                "Leap second not reported by hardware; using 18 s GPS–UTC offset.",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalFair
                            )
                        }
                    }
                }
            }

            // ── GPS Time & System Comparison ─────────────────────────────────
            LabSectionCard("GPS & Epoch Timing", origin = DataOrigin.HARDWARE) {
                MonoMetricRow("GPS Week", if (clockState.syncStatus == ClockSyncStatus.UNAVAILABLE) "—" else "${clockState.gpsWeek}")
                MonoMetricRow("Time of Week (TOW)", if (clockState.syncStatus == ClockSyncStatus.UNAVAILABLE) "—" else "${"%.6f".format(clockState.gpsTowSeconds)} s")
                MonoMetricRow("Unix Timestamp", if (clockState.syncStatus == ClockSyncStatus.UNAVAILABLE) "—" else "${"%.3f".format(clockState.unixTimestampSeconds)} s")
                MonoMetricRow(
                    "Leap-Second Offset",
                    if (clockState.isLeapSecondKnown) "${clockState.leapSecond} s (HW)" else "${clockState.leapSecond} s (Fallback)",
                    valueColor = if (clockState.isLeapSecondKnown) StarWhite else SignalFair
                )
                MonoMetricRow(
                    "GNSS − Android Clock",
                    if (clockState.syncStatus == ClockSyncStatus.UNAVAILABLE) "—" else "${"%+.3f".format(clockState.gnssMinusSystemMs)} ms",
                    valueColor = CosmicBlueLight,
                    hint = "Difference between GNSS-derived UTC and Android wall clock"
                )
                MonoMetricRow(
                    "Measurement Age",
                    if (!rawClock.isValid) "—" else "${"%.1f".format(clockState.measurementAgeMs)} ms"
                )
            }

            // ── Oscillator / Receiver Clock Telemetry ────────────────────────
            LabSectionCard("Local Oscillator & Bias", origin = DataOrigin.HARDWARE) {
                MonoMetricRow("Full Bias", if (rawClock.hasFullBiasNanos) "${clockState.fullBiasNanos} ns" else "Unsupported")
                MonoMetricRow("Sub-ns Bias", if (rawClock.hasBiasNanos) "${"%.3f".format(clockState.biasNanos)} ns" else "—")
                MonoMetricRow("Clock Drift", if (rawClock.hasDriftNanosPerSecond) "${"%+.2f".format(clockState.driftNanosPerSecond)} ns/s" else "Unsupported")
                MonoMetricRow("Drift Uncertainty", if (rawClock.hasDriftUncertaintyNanosPerSecond) "±${"%.2f".format(clockState.driftUncertaintyNanosPerSecond)} ns/s" else "—")
                MonoMetricRow(
                    "HW Clock Discontinuities",
                    "${clockState.discontinuityCount}",
                    valueColor = if (clockState.discontinuityCount > 0) SignalFair else SignalExcellent
                )
            }

            // ── Real-time Graphs ─────────────────────────────────────────────
            LabSectionCard("Clock Drift History (ns/s)", origin = DataOrigin.HARDWARE) {
                LabLineChart(
                    values = driftHistory,
                    lineColor = CosmicBlue,
                    unit = "ns/s",
                    showZeroLine = true
                )
            }

            LabSectionCard("GNSS − System Clock Offset (ms)", origin = DataOrigin.DERIVED) {
                LabLineChart(
                    values = offsetHistory,
                    lineColor = SignalExcellent,
                    unit = "ms",
                    showZeroLine = true
                )
            }

            ExpandableTechNote(
                title = "Display Resolution vs. True Timing Accuracy",
                explanation = "The digital readout displays microsecond-level monotonic interpolation between 1 Hz GNSS epochs, but actual absolute timing accuracy is bounded by the receiver's Bias Uncertainty (typically 10–100 ns when locked, or microseconds indoors). Android's system wall clock is synchronized via NTP/NITZ and typically differs from atomic GNSS UTC by several milliseconds."
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun formatNanos(ns: Double): String = when {
    ns >= 1_000_000_000.0 -> "${"%.2f".format(ns / 1_000_000_000.0)} s"
    ns >= 1_000_000.0 -> "${"%.2f".format(ns / 1_000_000.0)} ms"
    ns >= 1_000.0 -> "${"%.2f".format(ns / 1_000.0)} µs"
    else -> "${"%.1f".format(ns)} ns"
}
