package com.skysense.app.ui.screens.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.data.model.CapabilityItem
import com.skysense.app.data.model.CapabilityStatus
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.ui.components.*
import com.skysense.app.ui.theme.*
import com.skysense.app.util.performHapticConfirm

@Composable
fun HardwareInspectorScreen(
    repository: GnssRepository,
    onBack: () -> Unit
) {
    val report by repository.hardwareCapabilities.collectAsStateWithLifecycle()
    val view = LocalView.current

    LaunchedEffect(Unit) {
        repository.refreshHardwareCapabilities()
    }

    LabInstrumentScaffold(
        title = "Hardware Inspector",
        subtitle = "Receiver capabilities & RF profile",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                repository.refreshHardwareCapabilities()
                view.performHapticConfirm()
            }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = CosmicBlue)
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
            val observedCount = report.capabilities.count { it.isObserved }
            val supportedCount = report.capabilities.count { it.status == CapabilityStatus.SUPPORTED || it.isObserved }
            PlainSummaryBanner(
                headline = "${report.hardwareModel} (${if (report.hardwareYear > 0) report.hardwareYear else "GNSS"})",
                description = "Your device supports $supportedCount of ${report.capabilities.size} inspected features, with $observedCount actively observed in this session.",
                accentColor = CosmicBlue
            )
            QuickStatRow(
                listOf(
                    Triple("Bands", "${report.observedBands.size}", GalileoColor),
                    Triple("Systems", "${report.observedConstellations.size}", SignalExcellent),
                    Triple("Rate", if (report.measurementRateHz > 0f) "${"%.1f".format(report.measurementRateHz)} Hz" else "—", CosmicBlueLight)
                )
            )

            // ── Receiver Identity ────────────────────────────────────────────
            LabSectionCard("GNSS Chipset & OS", origin = DataOrigin.HARDWARE) {
                MonoMetricRow("Hardware Model", report.hardwareModel)
                MonoMetricRow(
                    "Hardware Year",
                    if (report.hardwareYear > 0) "${report.hardwareYear}" else "Not Reported"
                )
                MonoMetricRow("Android API Level", "API ${report.androidApiLevel}")
                MonoMetricRow(
                    "Update Rate",
                    if (report.measurementRateHz > 0f) "${"%.2f".format(report.measurementRateHz)} Hz" else "Awaiting epochs"
                )
                MonoMetricRow(
                    "Observed Constellations",
                    if (report.observedConstellations.isEmpty()) "None yet"
                    else report.observedConstellations.joinToString(", ") { it.shortName }
                )
                MonoMetricRow(
                    "Observed Bands",
                    if (report.observedBands.isEmpty()) "None yet"
                    else report.observedBands.sorted().joinToString(", ")
                )
            }

            // ── Capabilities grouped by category ─────────────────────────────
            val grouped = report.capabilities.groupBy { it.category }
            grouped.forEach { (category, items) ->
                LabSectionCard(category) {
                    items.forEachIndexed { index, item ->
                        CapabilityRow(item)
                        if (index < items.lastIndex) {
                            HorizontalDivider(color = SpaceDivider.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // ── Antenna Calibration Info ─────────────────────────────────────
            LabSectionCard("Antenna Calibration", origin = DataOrigin.HARDWARE) {
                if (report.antennaInfos.isEmpty()) {
                    Text(
                        "No GnssAntennaInfo profiles reported by this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DimGrey
                    )
                } else {
                    report.antennaInfos.forEach { ant ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MonoMetricRow("Carrier Frequency", "${"%.2f".format(ant.carrierFrequencyMHz)} MHz")
                            MonoMetricRow("Phase Center Offset", ant.phaseCenterOffsetMm)
                            MonoMetricRow("Phase Variations", if (ant.hasPhaseCenterVariations) "Available" else "None")
                            MonoMetricRow("Gain Corrections", if (ant.hasSignalGainCorrections) "Available" else "None")
                        }
                    }
                }
            }

            ExpandableTechNote(
                title = "How Capabilities Are Discovered",
                explanation = "SkySense queries Android's LocationManager.gnssCapabilities (API 30+), GnssAntennaInfo, and live GnssMeasurementsEvent telemetry. 'Observed Live' means real data has been received in this session; 'Supported' means the OS reports hardware support even if no signal is currently observed; 'Unknown' indicates the Android API level does not expose that flag."
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CapabilityRow(item: CapabilityItem) {
    val badgeColor = when (item.status) {
        CapabilityStatus.AVAILABLE_NOW -> SignalExcellent
        CapabilityStatus.SUPPORTED -> CosmicBlue
        CapabilityStatus.NOT_OBSERVED -> SignalFair
        CapabilityStatus.UNSUPPORTED -> SignalPoor
        CapabilityStatus.UNKNOWN -> DimGrey
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, color = StarWhite, fontWeight = FontWeight.Medium)
            Text(
                item.detail,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = DimGrey
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = badgeColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = item.status.label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
