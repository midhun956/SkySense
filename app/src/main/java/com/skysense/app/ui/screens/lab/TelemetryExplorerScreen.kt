package com.skysense.app.ui.screens.lab

import android.location.GnssMeasurement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.data.model.ConstellationType
import com.skysense.app.data.model.RawMeasurementData
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.domain.GnssMath
import com.skysense.app.ui.components.*
import com.skysense.app.ui.screens.dashboard.color
import com.skysense.app.ui.theme.*
import com.skysense.app.util.performHapticSegmentTick

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelemetryExplorerScreen(
    repository: GnssRepository,
    initialSvid: Int? = null,
    initialConstellation: String? = null,
    onBack: () -> Unit
) {
    val rawMeasurements by repository.rawMeasurements.collectAsStateWithLifecycle()
    val rawClock by repository.rawClock.collectAsStateWithLifecycle()
    val satellites by repository.liveSatellites.collectAsStateWithLifecycle()
    val navMessages by repository.navMessages.collectAsStateWithLifecycle()
    val view = LocalView.current

    var selectedKey by remember(initialSvid, initialConstellation) {
        val initConst = initialConstellation?.let { runCatching { ConstellationType.valueOf(it) }.getOrNull() }
        mutableStateOf<Triple<Int, ConstellationType, String>?>(
            if (initialSvid != null && initConst != null) Triple(initialSvid, initConst, "") else null
        )
    }

    // Auto-select first available measurement if none selected
    val activeMeasurement: RawMeasurementData? = remember(rawMeasurements, selectedKey) {
        val key = selectedKey
        if (key == null) rawMeasurements.firstOrNull()
        else rawMeasurements.find {
            it.svid == key.first && it.constellation == key.second &&
                (key.third.isEmpty() || it.frequencyBandLabel == key.third)
        } ?: rawMeasurements.find { it.svid == key.first && it.constellation == key.second }
            ?: rawMeasurements.firstOrNull()
    }

    val cn0History = remember { mutableStateListOf<Float>() }
    val prRateHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(activeMeasurement?.receivedElapsedRealtimeNanos, activeMeasurement?.svid, activeMeasurement?.frequencyBandLabel) {
        val m = activeMeasurement ?: return@LaunchedEffect
        cn0History.add(m.cn0DbHz.toFloat())
        if (cn0History.size > 60) cn0History.removeAt(0)
        prRateHistory.add(m.pseudorangeRateMetersPerSecond.toFloat())
        if (prRateHistory.size > 60) prRateHistory.removeAt(0)
    }

    // Reset history when switching satellite
    LaunchedEffect(activeMeasurement?.svid, activeMeasurement?.constellation, activeMeasurement?.frequencyBandLabel) {
        cn0History.clear()
        prRateHistory.clear()
    }

    LabInstrumentScaffold(
        title = "Telemetry Explorer",
        subtitle = "Per-satellite raw GNSS observables",
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
            if (rawMeasurements.isEmpty()) {
                LabSectionCard("Awaiting Raw Measurements") {
                    Text(
                        "No GnssMeasurement epochs received yet. Move outdoors with a clear sky view, or check Hardware Inspector to confirm raw measurement support.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoonGrey
                    )
                }
            } else {
                // ── Satellite Selector Strip ─────────────────────────────────
                Text(
                    "Select Tracked Channel (${rawMeasurements.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = DimGrey
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rawMeasurements.forEach { m ->
                        val isSelected = activeMeasurement?.svid == m.svid &&
                            activeMeasurement.constellation == m.constellation &&
                            activeMeasurement.frequencyBandLabel == m.frequencyBandLabel
                        val cColor = m.constellation.color()
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                view.performHapticSegmentTick()
                                selectedKey = Triple(m.svid, m.constellation, m.frequencyBandLabel)
                            },
                            label = {
                                Text(
                                    "${m.constellation.shortName}-${m.svid} ${m.frequencyBandLabel}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            },
                            leadingIcon = {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(cColor))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cColor.copy(alpha = 0.22f),
                                selectedLabelColor = StarWhite
                            )
                        )
                    }
                }

                activeMeasurement?.let { m ->
                    val statusMatch = satellites.find { it.svid == m.svid && it.constellation == m.constellation }
                    val pseudorange = remember(m, rawClock) { GnssMath.computePseudorangeMeters(m, rawClock) }
                    val doppler = remember(m) { GnssMath.computeDopplerShiftHz(m.pseudorangeRateMetersPerSecond, m.carrierFrequencyHz) }
                    val propMs = pseudorange?.let { GnssMath.computeSignalPropagationMs(it) }
                    val prUncertaintyM = (m.receivedSvTimeUncertaintyNanos / 1e9) * GnssMath.SPEED_OF_LIGHT_MPS

                    PlainSummaryBanner(
                        headline = "${m.constellation.displayName} Satellite #${m.svid} (${m.frequencyBandLabel})",
                        description = "Pick any tracked satellite above to inspect its distance from you, speed (Doppler), carrier phase, and raw tracking flags.",
                        accentColor = m.constellation.color()
                    )
                    QuickStatRow(
                        listOf(
                            Triple("Distance", pseudorange?.let { "${"%.0f".format(it / 1000.0)} km" } ?: "—", CosmicBlueLight),
                            Triple("Signal", "${"%.1f".format(m.cn0DbHz)} dB", SignalExcellent),
                            Triple("Doppler", doppler?.let { "${"%+.0f".format(it)} Hz" } ?: "—", GalileoColor)
                        )
                    )

                    // ── Ranging & Doppler ────────────────────────────────────
                    LabSectionCard("Ranging & Doppler", origin = DataOrigin.DERIVED) {
                        MonoMetricRow(
                            "Pseudorange",
                            if (pseudorange != null) "${"%.2f".format(pseudorange / 1000.0)} km (${"%.1f".format(pseudorange)} m)"
                            else "Unavailable (TOW/Bias)"
                        )
                        MonoMetricRow("SV Time Uncertainty", "±${m.receivedSvTimeUncertaintyNanos} ns (±${"%.2f".format(prUncertaintyM)} m)")
                        MonoMetricRow(
                            "Signal Propagation",
                            if (propMs != null) "${"%.3f".format(propMs)} ms" else "—"
                        )
                        MonoMetricRow(
                            "Pseudorange Rate",
                            "${"%+.3f".format(m.pseudorangeRateMetersPerSecond)} ± ${"%.3f".format(m.pseudorangeRateUncertaintyMetersPerSecond)} m/s"
                        )
                        MonoMetricRow(
                            "Doppler Shift",
                            if (doppler != null) "${"%+.2f".format(doppler)} Hz" else "No carrier freq"
                        )
                    }

                    // ── Signal & Carrier ─────────────────────────────────────
                    LabSectionCard("Signal & Carrier", origin = DataOrigin.HARDWARE) {
                        MonoMetricRow("Carrier Frequency", if (m.hasCarrierFrequencyHz) "${"%.4f".format(m.carrierFrequencyHz / 1e6f)} MHz (${m.frequencyBandLabel})" else "Not Reported")
                        MonoMetricRow("Code Type", if (m.hasCodeType && m.codeType.isNotBlank()) m.codeType else "Not Reported")
                        MonoMetricRow("Antenna C/N₀", "${"%.2f".format(m.cn0DbHz)} dB-Hz")
                        MonoMetricRow("Baseband C/N₀", if (m.hasBasebandCn0DbHz) "${"%.2f".format(m.basebandCn0DbHz)} dB-Hz" else "Unsupported")
                        MonoMetricRow("Multipath Indicator", multipathLabel(m.multipathIndicator), valueColor = multipathColor(m.multipathIndicator))
                        if (statusMatch != null) {
                            MonoMetricRow("Elevation / Azimuth", "${"%.1f".format(statusMatch.elevationDegrees)}° / ${"%.1f".format(statusMatch.azimuthDegrees)}°")
                        }
                    }

                    // ── Carrier Phase / ADR ──────────────────────────────────
                    val adrFlags = GnssMath.decodeAdrStateFlags(m.accumulatedDeltaRangeState)
                    val isAdrValid = (m.accumulatedDeltaRangeState and GnssMeasurement.ADR_STATE_VALID) != 0
                    val hasCycleSlip = (m.accumulatedDeltaRangeState and GnssMeasurement.ADR_STATE_CYCLE_SLIP) != 0
                    LabSectionCard("Carrier Phase (ADR)", origin = DataOrigin.HARDWARE) {
                        MonoMetricRow(
                            "Accumulated Delta Range",
                            "${"%.4f".format(m.accumulatedDeltaRangeMeters)} ± ${"%.4f".format(m.accumulatedDeltaRangeUncertaintyMeters)} m",
                            valueColor = if (isAdrValid) SignalExcellent else DimGrey
                        )
                        MonoMetricRow(
                            "Cycle Slip Detected",
                            if (hasCycleSlip) "YES (Phase Jump)" else "No",
                            valueColor = if (hasCycleSlip) SignalPoor else SignalExcellent
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            adrFlags.forEach { flag ->
                                FlagChip(flag, if (flag == "VALID") SignalExcellent else if (flag == "CYCLE_SLIP" || flag == "RESET") SignalFair else MoonGrey)
                            }
                        }
                    }

                    // ── Measurement State Flags ──────────────────────────────
                    val stateFlags = GnssMath.decodeMeasurementStateFlags(m.state)
                    LabSectionCard("Tracking State & Timing", origin = DataOrigin.HARDWARE) {
                        MonoMetricRow("Received SV Time", "${m.receivedSvTimeNanos} ns")
                        MonoMetricRow("Time Offset", "${"%.2f".format(m.timeOffsetNanos)} ns")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            stateFlags.forEach { flag ->
                                FlagChip(flag, CosmicBlueLight)
                            }
                        }
                    }

                    // ── Satellite PVT (if reported) ──────────────────────────
                    LabSectionCard("Satellite PVT & Clock", origin = DataOrigin.HARDWARE) {
                        val pvt = m.satellitePvt
                        if (pvt == null) {
                            Text(
                                "Hardware Satellite PVT is not reported on this device/epoch (requires Android 14+ HAL support).",
                                style = MaterialTheme.typography.bodySmall,
                                color = DimGrey
                            )
                        } else {
                            MonoMetricRow("ECEF X / Y / Z", "${"%.1f".format(pvt.posXMeters)}, ${"%.1f".format(pvt.posYMeters)}, ${"%.1f".format(pvt.posZMeters)} m")
                            MonoMetricRow("Vel X / Y / Z", "${"%.2f".format(pvt.velXMps)}, ${"%.2f".format(pvt.velYMps)}, ${"%.2f".format(pvt.velZMps)} m/s")
                            MonoMetricRow("SV Clock Bias / Drift", "${"%.3f".format(pvt.clockBiasMeters)} m / ${"%.4f".format(pvt.clockDriftMps)} m/s")
                            MonoMetricRow("Iono / Tropo Delay", "${"%.2f".format(pvt.ionoDelayMeters)} m / ${"%.2f".format(pvt.tropoDelayMeters)} m")
                        }
                    }

                    // ── Navigation Messages ──────────────────────────────────
                    val satNavMsgs = navMessages.filter { it.svid == m.svid }.takeLast(5).reversed()
                    LabSectionCard("Navigation Messages (${satNavMsgs.size})", origin = DataOrigin.HARDWARE) {
                        if (satNavMsgs.isEmpty()) {
                            Text(
                                "No GnssNavigationMessage subframes buffered for PRN ${m.svid} yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DimGrey
                            )
                        } else {
                            satNavMsgs.forEach { nav ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SpaceDark, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        "${nav.typeLabel} • Msg ${nav.messageId} / Sub ${nav.submessageId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CosmicBlueLight,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        nav.dataHex.take(64) + if (nav.dataHex.length > 64) "…" else "",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MoonGrey
                                    )
                                }
                            }
                        }
                    }

                    // ── Historical Graphs ────────────────────────────────────
                    LabSectionCard("C/N₀ History (dB-Hz)", origin = DataOrigin.HARDWARE) {
                        LabLineChart(cn0History, SignalExcellent, "dB-Hz")
                    }
                    LabSectionCard("Pseudorange Rate History (m/s)", origin = DataOrigin.HARDWARE) {
                        LabLineChart(prRateHistory, CosmicBlue, "m/s", showZeroLine = true)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FlagChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = color
        )
    }
}

private fun multipathLabel(indicator: Int): String = when (indicator) {
    GnssMeasurement.MULTIPATH_INDICATOR_DETECTED -> "Detected"
    GnssMeasurement.MULTIPATH_INDICATOR_NOT_DETECTED -> "Clean (None)"
    else -> "Unknown"
}

private fun multipathColor(indicator: Int) = when (indicator) {
    GnssMeasurement.MULTIPATH_INDICATOR_DETECTED -> SignalPoor
    GnssMeasurement.MULTIPATH_INDICATOR_NOT_DETECTED -> SignalExcellent
    else -> DimGrey
}
