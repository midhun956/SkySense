package com.skysense.app.ui.screens.lab

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.domain.AstronomyEngine
import com.skysense.app.domain.ClockSyncStatus
import com.skysense.app.domain.PrecisionClockEngine
import com.skysense.app.ui.components.*
import com.skysense.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun AstronomyScreen(
    repository: GnssRepository,
    onBack: () -> Unit
) {
    val snapshot by repository.liveSnapshot.collectAsStateWithLifecycle()
    val rawClock by repository.rawClock.collectAsStateWithLifecycle()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var nowElapsedNanos by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            nowElapsedNanos = SystemClock.elapsedRealtimeNanos()
            delay(1000L)
        }
    }

    val clockState = remember(rawClock, nowElapsedNanos) {
        PrecisionClockEngine.compute(rawClock, nowElapsedNanos, nowMs)
    }
    val useGnssTime = clockState.syncStatus != ClockSyncStatus.UNAVAILABLE
    val unixSeconds = if (useGnssTime) clockState.unixTimestampSeconds else nowMs / 1000.0

    val report = remember(unixSeconds.toLong(), snapshot.latitude, snapshot.longitude) {
        AstronomyEngine.compute(
            unixSeconds = unixSeconds,
            latitudeDeg = if (snapshot.hasValidFix) snapshot.latitude else 0.0,
            longitudeDeg = if (snapshot.hasValidFix) snapshot.longitude else 0.0
        )
    }

    LabInstrumentScaffold(
        title = "Astronomical Instruments",
        subtitle = "Offline celestial & sidereal calculations",
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
                headline = "${report.moonPhaseName} • Daylight ${report.daylightDurationText}",
                description = "All sunrise, twilight, Sun, Moon, and sidereal time calculations run 100% offline using your GNSS time and position.",
                accentColor = GalileoColor
            )
            QuickStatRow(
                listOf(
                    Triple("Sunrise", report.sunriseLocal, SignalFair),
                    Triple("Sunset", report.sunsetLocal, GalileoColor),
                    Triple("Moon", "${"%.0f".format(report.moonIlluminationPct)}%", CosmicBlueLight)
                )
            )

            if (!snapshot.hasValidFix) {
                LabSectionCard("Location Required for Local Az/El") {
                    Text(
                        "Awaiting GNSS fix; showing calculations for 0.0°N, 0.0°E until location locks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SignalFair
                    )
                }
            }

            LabSectionCard("Astronomical & Sidereal Time", origin = if (useGnssTime) DataOrigin.HARDWARE else DataOrigin.DERIVED) {
                MonoMetricRow("Time Source", if (useGnssTime) "GNSS Atomic UTC" else "System Clock (Fallback)")
                MonoMetricRow("Julian Date (JD)", "${"%.5f".format(report.julianDate)}")
                MonoMetricRow("Modified Julian Date", "${"%.5f".format(report.modifiedJulianDate)}")
                MonoMetricRow("GMST", AstronomyEngine.formatHoursHms(report.gmstHours))
                MonoMetricRow("Local Sidereal Time (LST)", AstronomyEngine.formatHoursHms(report.lstHours), valueColor = CosmicBlueLight)
                MonoMetricRow("Local Solar Time", AstronomyEngine.formatHoursHms(report.localSolarTimeHours), valueColor = GalileoColor)
            }

            LabSectionCard("Solar Ephemeris & Twilight", origin = DataOrigin.DERIVED) {
                MonoMetricRow("Sun Azimuth / Elevation", "${"%.2f".format(report.sunAzimuthDeg)}° / ${"%+.2f".format(report.sunElevationDeg)}°")
                MonoMetricRow("Sunrise / Sunset", "${report.sunriseLocal} / ${report.sunsetLocal}")
                MonoMetricRow("Solar Noon", report.solarNoonLocal)
                MonoMetricRow("Daylight Duration", report.daylightDurationText)
                MonoMetricRow("Civil Twilight (−6°)", report.civilTwilight)
                MonoMetricRow("Nautical Twilight (−12°)", report.nauticalTwilight)
                MonoMetricRow("Astronomical Twilight (−18°)", report.astronomicalTwilight)
            }

            LabSectionCard("Lunar Ephemeris", origin = DataOrigin.DERIVED) {
                MonoMetricRow("Moon Phase", "${report.moonPhaseName} (${"%.1f".format(report.moonIlluminationPct)}%)")
                MonoMetricRow("Moon Azimuth / Elevation", "${"%.2f".format(report.moonAzimuthDeg)}° / ${"%+.2f".format(report.moonElevationDeg)}°")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
