package com.skysense.app.ui.screens.lab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.domain.GnssDataRecorder
import com.skysense.app.domain.RecordedEpoch
import com.skysense.app.ui.components.*
import com.skysense.app.ui.theme.*
import com.skysense.app.util.performHapticConfirm
import com.skysense.app.util.performHapticReject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    CSV("CSV (Raw Measurements)", "csv", "text/csv"),
    JSON("JSON (Full Session)", "json", "application/json"),
    NMEA("NMEA 0183 Sentences", "nmea", "text/plain"),
    GPX("GPX 1.1 Track", "gpx", "application/gpx+xml"),
    RINEX("RINEX 3.04 Obs", "obs", "text/plain")
}

@Composable
fun DataRecorderScreen(
    repository: GnssRepository,
    onBack: () -> Unit
) {
    val capabilities by repository.hardwareCapabilities.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val recorder = remember { GnssDataRecorder() }
    var isRecording by remember { mutableStateOf(false) }
    var epochCount by remember { mutableIntStateOf(0) }
    var measCount by remember { mutableIntStateOf(0) }
    var nmeaCount by remember { mutableIntStateOf(0) }
    var navCount by remember { mutableIntStateOf(0) }
    var durationSec by remember { mutableLongStateOf(0L) }
    var statusMsg by remember { mutableStateOf("Ready to record raw GNSS telemetry") }
    var pendingFormat by remember { mutableStateOf<ExportFormat?>(null) }

    // Timer for recording duration
    LaunchedEffect(isRecording) {
        while (isRecording) {
            durationSec = (System.currentTimeMillis() - recorder.startTimeMs) / 1000L
            delay(1000L)
        }
    }

    // Collect streams only while recording
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        launch {
            repository.measurementEpochEvents.collect { (clock, meas) ->
                recorder.epochs.add(
                    RecordedEpoch(System.currentTimeMillis(), clock, meas, repository.liveSnapshot.value)
                )
                epochCount = recorder.epochs.size
                measCount = recorder.totalMeasurementCount()
            }
        }
        launch {
            repository.nmeaEvents.collect { nmea ->
                recorder.nmeaLines.add(nmea)
                nmeaCount = recorder.nmeaLines.size
            }
        }
        launch {
            repository.navEvents.collect { nav ->
                recorder.navMessages.add(nav)
                navCount = recorder.navMessages.size
            }
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val fmt = pendingFormat ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = when (fmt) {
                        ExportFormat.CSV -> recorder.exportCsv()
                        ExportFormat.JSON -> recorder.exportJson()
                        ExportFormat.NMEA -> recorder.exportNmea()
                        ExportFormat.GPX -> recorder.exportGpx()
                        ExportFormat.RINEX -> recorder.exportRinex304()
                    }
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open output stream")
                }
            }
            result.onSuccess {
                statusMsg = "Exported ${fmt.label} successfully"
                view.performHapticConfirm()
            }.onFailure { err ->
                statusMsg = "Export failed: ${err.message}"
                view.performHapticReject()
            }
        }
    }

    LabInstrumentScaffold(
        title = "Raw GNSS Data Recorder",
        subtitle = "Record & export CSV, JSON, NMEA, GPX, RINEX",
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
                headline = if (isRecording) "Recording Live GNSS Session…" else "1. Record  →  2. Stop  →  3. Export",
                description = "Capture raw satellite measurements, NMEA sentences, and track points, then save as GPX (maps), CSV (spreadsheets), JSON, or RINEX.",
                accentColor = IrnssColor
            )

            LabSectionCard("Recording Session", origin = DataOrigin.HARDWARE) {
                MonoMetricRow(
                    "Status",
                    if (isRecording) "RECORDING" else "IDLE",
                    valueColor = if (isRecording) SignalPoor else MoonGrey
                )
                MonoMetricRow("Duration", "%02d:%02d".format(durationSec / 60, durationSec % 60))
                MonoMetricRow("Epochs / Measurements", "$epochCount / $measCount")
                MonoMetricRow("NMEA Sentences", "$nmeaCount")
                MonoMetricRow("Nav Messages", "$navCount")
                MonoMetricRow("Est. Buffer Size", "${recorder.estimatedSizeKb()} KB")
                Text(statusMsg, style = MaterialTheme.typography.labelSmall, color = CosmicBlueLight)

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (!isRecording) {
                            recorder.reset(capabilities)
                            epochCount = 0; measCount = 0; nmeaCount = 0; navCount = 0; durationSec = 0L
                            isRecording = true
                            statusMsg = "Capturing raw GNSS measurements, NMEA & Nav messages…"
                            view.performHapticConfirm()
                        } else {
                            isRecording = false
                            statusMsg = "Recording stopped ($epochCount epochs captured)"
                            view.performHapticConfirm()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) SignalPoor else CosmicBlueDark,
                        contentColor = StarWhite
                    )
                ) {
                    Icon(if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRecording) "Stop Recording" else "Start Recording")
                }
            }

            LabSectionCard("Export Recorded Data", origin = DataOrigin.DERIVED) {
                val hasData = epochCount > 0 || nmeaCount > 0
                if (!hasData) {
                    Text("Record at least one epoch or NMEA sentence to export.", style = MaterialTheme.typography.bodySmall, color = DimGrey)
                }
                ExportFormat.entries.forEach { fmt ->
                    OutlinedButton(
                        onClick = {
                            pendingFormat = fmt
                            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            createDocLauncher.launch("skysense_$ts.${fmt.ext}")
                        },
                        enabled = hasData && !isRecording,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export ${fmt.label}")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
