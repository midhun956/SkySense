package com.skysense.app.ui.screens.lab

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.data.repository.EnvironmentRepository
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.ui.components.*
import com.skysense.app.ui.theme.*
import kotlin.math.abs

@Composable
fun AltimeterScreen(
    repository: GnssRepository,
    environmentRepository: EnvironmentRepository,
    onBack: () -> Unit
) {
    val snapshot by repository.liveSnapshot.collectAsStateWithLifecycle()
    val mslAltFlow by repository.mslAltitudeMeters.collectAsStateWithLifecycle()
    val nmeaGga by repository.nmeaGgaInfo.collectAsStateWithLifecycle()
    val envData by environmentRepository.environmentData.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Barometer sensor (if device has TYPE_PRESSURE)
    var pressureHpa by remember { mutableStateOf<Float?>(null) }
    var hasBarometer by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val baro = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
        hasBarometer = baro != null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                pressureHpa = event.values.firstOrNull()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (baro != null) {
            sm.registerListener(listener, baro, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            if (baro != null) sm.unregisterListener(listener)
        }
    }

    // Track altitude history, vertical speed, gain/loss
    val altHistory = remember { mutableStateListOf<Float>() }
    var prevAlt by remember { mutableStateOf<Double?>(null) }
    var prevTimeMs by remember { mutableStateOf<Long?>(null) }
    var verticalSpeedMps by remember { mutableStateOf(0.0) }
    var totalGainM by remember { mutableDoubleStateOf(0.0) }
    var totalLossM by remember { mutableDoubleStateOf(0.0) }

    // Resolve WGS84, Geoid Separation, MSL Altitude, and Terrain Elevation
    val wgs84Alt = if (snapshot.hasValidFix) snapshot.altitude else null
    val geoidSep = nmeaGga?.geoidSeparationMeters ?: run {
        if (wgs84Alt != null && mslAltFlow != null) wgs84Alt - mslAltFlow!! else null
    }
    val mslAlt = mslAltFlow ?: nmeaGga?.mslAltitudeMeters ?: run {
        if (wgs84Alt != null && geoidSep != null) wgs84Alt - geoidSep else null
    }
    val terrainElev = envData?.elevationMeters
    val heightAboveTerrain = if (mslAlt != null && terrainElev != null) mslAlt - terrainElev else null
    val baroAlt = pressureHpa?.let { SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it).toDouble() }
    val fixAgeSec = if (snapshot.hasValidFix) ((System.currentTimeMillis() - snapshot.timestamp).coerceAtLeast(0L) / 1000.0) else null

    LaunchedEffect(snapshot.timestamp, snapshot.hasValidFix) {
        if (!snapshot.hasValidFix) return@LaunchedEffect
        val currentAlt = mslAlt ?: snapshot.altitude
        altHistory.add(currentAlt.toFloat())
        if (altHistory.size > 60) altHistory.removeAt(0)

        val pA = prevAlt
        val pT = prevTimeMs
        if (pA != null && pT != null && snapshot.timestamp > pT) {
            val dt = (snapshot.timestamp - pT) / 1000.0
            val dAlt = currentAlt - pA
            verticalSpeedMps = dAlt / dt
            if (abs(dAlt) > 0.3 && abs(dAlt) < 50.0) {
                if (dAlt > 0) totalGainM += dAlt else totalLossM += abs(dAlt)
            }
        }
        prevAlt = currentAlt
        prevTimeMs = snapshot.timestamp
    }

    LabInstrumentScaffold(
        title = "Advanced Altimeter",
        subtitle = "WGS84, Geoid MSL, Terrain & Barometer",
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
                headline = if (mslAlt != null) "Sea-Level Altitude Available" else "WGS84 Ellipsoidal Altitude",
                description = "Compares raw satellite height (WGS84), true Mean Sea Level height (MSL), ground terrain elevation, and your barometer.",
                accentColor = GalileoColor
            )

            // ── Primary MSL / WGS84 Readout ──────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SpaceCardElevated)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (mslAlt != null) "ORTHOMETRIC ALTITUDE (MSL)" else "WGS84 ELLIPSOIDAL ALTITUDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MoonGrey
                        )
                        OriginBadge(if (mslAlt != null) DataOrigin.DERIVED else DataOrigin.HARDWARE)
                    }
                    Text(
                        text = when {
                            mslAlt != null -> "${"%.1f".format(mslAlt)} m"
                            wgs84Alt != null -> "${"%.1f".format(wgs84Alt)} m"
                            else -> "— m"
                        },
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        ),
                        color = StarWhite
                    )
                    if (fixAgeSec != null && fixAgeSec > 5.0) {
                        Text(
                            "Location data is ${"%.0f".format(fixAgeSec)} s old (stale)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalFair
                        )
                    }
                }
            }

            // ── Vertical Reference Breakdown ─────────────────────────────────
            LabSectionCard("Vertical Reference Systems", origin = DataOrigin.DERIVED) {
                MonoMetricRow("WGS84 Ellipsoidal Height (h)", wgs84Alt?.let { "${"%.2f".format(it)} m" } ?: "No Fix")
                MonoMetricRow("Mean Sea Level (MSL / H)", mslAlt?.let { "${"%.2f".format(it)} m" } ?: "Awaiting NMEA GGA / API 34")
                MonoMetricRow("Geoid Separation (N = h − H)", geoidSep?.let { "${"%+.2f".format(it)} m" } ?: "Unknown")
                MonoMetricRow("Terrain Elevation (DEM)", terrainElev?.let { "${"%.1f".format(it)} m" } ?: "Offline / Unavailable")
                MonoMetricRow(
                    "Est. Height Above Terrain",
                    heightAboveTerrain?.let { "${"%+.1f".format(it)} m" } ?: "Needs MSL + Terrain",
                    valueColor = if (heightAboveTerrain != null) CosmicBlueLight else DimGrey
                )
            }

            // ── Dynamics & Precision ─────────────────────────────────────────
            LabSectionCard("Vertical Dynamics", origin = DataOrigin.HARDWARE) {
                MonoMetricRow("Vertical Accuracy (1σ)", if (snapshot.verticalAccuracy > 0f) "±${"%.1f".format(snapshot.verticalAccuracy)} m" else "Not Reported")
                MonoMetricRow("Vertical Speed", "${"%+.2f".format(verticalSpeedMps)} m/s")
                MonoMetricRow("Session Gain / Loss", "+${"%.1f".format(totalGainM)} m / −${"%.1f".format(totalLossM)} m")
            }

            // ── Barometric Altimeter ─────────────────────────────────────────
            LabSectionCard("Barometric Sensor", origin = DataOrigin.HARDWARE) {
                if (!hasBarometer) {
                    Text("This device does not include a barometric pressure sensor.", style = MaterialTheme.typography.bodySmall, color = DimGrey)
                } else {
                    MonoMetricRow("Atmospheric Pressure", pressureHpa?.let { "${"%.2f".format(it)} hPa" } ?: "Reading…")
                    MonoMetricRow("Pressure Altitude (ISA)", baroAlt?.let { "${"%.1f".format(it)} m" } ?: "—")
                }
            }

            // ── Altitude History Chart ───────────────────────────────────────
            LabSectionCard("Altitude History (m)", origin = DataOrigin.DERIVED) {
                LabLineChart(altHistory, GalileoColor, "m")
            }

            ExpandableTechNote(
                title = "Why GNSS Altitude Differs from Terrain Elevation",
                explanation = "Android's Location.getAltitude() reports height above the mathematical WGS84 ellipsoid (h), whereas topographic maps and Open-Meteo terrain elevation report orthometric height above Mean Sea Level / the Earth's Geoid (H). In many regions, the Geoid Separation (N) is 20–100 meters! SkySense resolves this using NMEA GGA geoid separation and Android 14+ MslAltitude so H = h − N matches terrain elevation."
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
