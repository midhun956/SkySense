package com.skysense.app.ui.screens.lab

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.ui.components.*
import com.skysense.app.ui.theme.*
import com.skysense.app.util.performHapticConfirm
import com.skysense.app.util.performHapticReject
import com.skysense.app.util.performHapticSegmentTick
import com.skysense.app.util.performHapticToggleOff
import com.skysense.app.util.performHapticToggleOn

@Composable
fun ReceiverControlsScreen(
    repository: GnssRepository,
    onBack: () -> Unit
) {
    val ctrl by repository.receiverControlState.collectAsStateWithLifecycle()
    val snapshot by repository.liveSnapshot.collectAsStateWithLifecycle()
    val view = LocalView.current

    var showDeleteAidingConfirm by remember { mutableStateOf(false) }

    LabInstrumentScaffold(
        title = "Receiver Controls",
        subtitle = "Tracking modes, intervals & A-GNSS",
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
            PlainSummaryBanner(
                headline = "Direct Receiver Tuning",
                description = "Adjust how aggressively the GNSS chip tracks satellites, measure Time-To-First-Fix (TTFF), or trigger A-GNSS assistance.",
                accentColor = SignalGood
            )

            // ── Battery Warning ──────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SignalFair.copy(alpha = 0.14f)
            ) {
                Text(
                    "Power Notice: Full GNSS tracking and high-performance modes disable receiver duty-cycling and significantly increase battery drain.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = SignalFair
                )
            }

            // ── Tracking Modes ───────────────────────────────────────────────
            LabSectionCard("Measurement Mode (Native API)", origin = DataOrigin.HARDWARE) {
                val supportsRequestApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Full GNSS Tracking", style = MaterialTheme.typography.bodyMedium, color = StarWhite)
                        Text(
                            if (supportsRequestApi) "Disables clock duty-cycling for continuous ADR"
                            else "Requires Android 12+ (API 31)",
                            style = MaterialTheme.typography.labelSmall,
                            color = DimGrey
                        )
                    }
                    Switch(
                        checked = ctrl.fullTrackingEnabled,
                        enabled = supportsRequestApi,
                        onCheckedChange = {
                            repository.applyReceiverControls(fullTracking = it)
                            if (it) view.performHapticToggleOn() else view.performHapticToggleOff()
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("High-Performance Mode", style = MaterialTheme.typography.bodyMedium, color = StarWhite)
                        Text("Full tracking + fastest 500 ms location interval", style = MaterialTheme.typography.labelSmall, color = DimGrey)
                    }
                    Switch(
                        checked = ctrl.highPerformanceMode,
                        onCheckedChange = {
                            repository.applyReceiverControls(highPerformance = it)
                            if (it) view.performHapticToggleOn() else view.performHapticToggleOff()
                        }
                    )
                }

                Text("Measurement Interval", style = MaterialTheme.typography.labelMedium, color = DimGrey)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1000, 2000, 5000).forEach { ms ->
                        FilterChip(
                            selected = ctrl.measurementIntervalMs == ms && !ctrl.highPerformanceMode,
                            enabled = supportsRequestApi && !ctrl.highPerformanceMode,
                            onClick = {
                                view.performHapticSegmentTick()
                                repository.applyReceiverControls(intervalMs = ms)
                            },
                            label = { Text("${ms / 1000}s") }
                        )
                    }
                }
            }

            // ── Acquisition & TTFF Diagnostics ───────────────────────────────
            LabSectionCard("Acquisition & TTFF", origin = DataOrigin.HARDWARE) {
                MonoMetricRow("Measurement Rate", "${"%.2f".format(ctrl.measurementRateHz)} Hz")
                MonoMetricRow("Satellites Used / Visible", "${snapshot.satellitesUsed} / ${snapshot.satellitesVisible}")
                MonoMetricRow(
                    "Last Measured TTFF",
                    when {
                        ctrl.isMeasuringTtff -> "Measuring…"
                        ctrl.ttffMillis != null -> "${ctrl.ttffMillis} ms"
                        else -> "Not Measured"
                    },
                    valueColor = if (ctrl.ttffMillis != null) SignalExcellent else MoonGrey
                )
                MonoMetricRow("HAL Status", ctrl.statusMessage, valueColor = CosmicBlueLight)

                Button(
                    onClick = {
                        view.performHapticSegmentTick()
                        repository.startTtffMeasurement()
                    },
                    enabled = !ctrl.isMeasuringTtff,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (ctrl.isMeasuringTtff) "Waiting for First Fix…" else "Measure TTFF (Re-acquire)")
                }
            }

            // ── A-GNSS Assistance Commands ───────────────────────────────────
            LabSectionCard("A-GNSS Assistance Commands", origin = DataOrigin.HARDWARE) {
                OutlinedButton(
                    onClick = {
                        val ok = repository.sendAgnssCommand("force_xtra_injection")
                        if (ok) view.performHapticConfirm() else view.performHapticReject()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Force XTRA / Ephemeris Injection")
                }
                OutlinedButton(
                    onClick = {
                        val ok = repository.sendAgnssCommand("force_time_injection")
                        if (ok) view.performHapticConfirm() else view.performHapticReject()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Force A-GNSS Time Injection")
                }
                OutlinedButton(
                    onClick = { showDeleteAidingConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalPoor)
                ) {
                    Text("Delete Aiding Data (Cold Start)")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeleteAidingConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAidingConfirm = false },
            title = { Text("Delete Aiding Data?", color = StarWhite) },
            text = {
                Text(
                    "This clears cached ephemeris, almanac, and time aiding data in the GNSS receiver to simulate a true cold start.",
                    color = MoonGrey
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAidingConfirm = false
                    val ok = repository.sendAgnssCommand("delete_aiding_data")
                    if (ok) view.performHapticConfirm() else view.performHapticReject()
                }) {
                    Text("Delete", color = SignalPoor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAidingConfirm = false }) {
                    Text("Cancel", color = MoonGrey)
                }
            },
            containerColor = SpaceCard
        )
    }
}
