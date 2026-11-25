package com.skysense.app.ui.screens.lab

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.domain.OrbitalPropagator
import com.skysense.app.domain.TleEntry
import com.skysense.app.ui.components.*
import com.skysense.app.ui.screens.dashboard.color
import com.skysense.app.ui.theme.*
import com.skysense.app.util.performHapticConfirm
import com.skysense.app.util.performHapticReject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

private const val TLE_CACHE_FILENAME = "gnss_tle_cache.txt"
private const val CELESTRAK_GNSS_URL = "https://celestrak.org/NORAD/elements/gp.php?GROUP=gnss&FORMAT=tle"

@Composable
fun SatellitePredictionScreen(
    repository: GnssRepository,
    onBack: () -> Unit
) {
    val snapshot by repository.liveSnapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    var catalog by remember { mutableStateOf<List<TleEntry>>(emptyList()) }
    var cacheTimestampMs by remember { mutableLongStateOf(0L) }
    var isFetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var offsetHours by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val file = File(context.filesDir, TLE_CACHE_FILENAME)
        if (file.exists()) {
            val text = withContext(Dispatchers.IO) { file.readText() }
            catalog = OrbitalPropagator.parseTleCatalog(text)
            cacheTimestampMs = file.lastModified()
        }
    }

    val obsLat = if (snapshot.hasValidFix) snapshot.latitude else 0.0
    val obsLon = if (snapshot.hasValidFix) snapshot.longitude else 0.0
    val targetUnixSec = System.currentTimeMillis() / 1000.0 + offsetHours * 3600.0

    val predicted = remember(catalog, offsetHours, obsLat, obsLon) {
        if (catalog.isEmpty()) emptyList()
        else OrbitalPropagator.predictVisibleSatellites(catalog, targetUnixSec, obsLat, obsLon)
    }
    val predictedPdop = remember(predicted) {
        if (predicted.size < 4) null else OrbitalPropagator.estimateFuturePdop(predicted)
    }

    // 24h PDOP curve every 1 hour
    val pdopForecast = remember(catalog, obsLat, obsLon) {
        if (catalog.isEmpty()) emptyList()
        else (0..24).map { h ->
            val t = System.currentTimeMillis() / 1000.0 + h * 3600.0
            val sats = OrbitalPropagator.predictVisibleSatellites(catalog, t, obsLat, obsLon)
            h to OrbitalPropagator.estimateFuturePdop(sats)
        }
    }
    val bestWindow = remember(pdopForecast) {
        pdopForecast.minByOrNull { it.second }
    }

    val timeFmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }

    LabInstrumentScaffold(
        title = "Satellite Prediction",
        subtitle = "TLE orbital visibility & PDOP forecast",
        onBack = onBack,
        actions = {
            IconButton(
                enabled = !isFetching,
                onClick = {
                    isFetching = true
                    fetchError = null
                    scope.launch {
                        val res = fetchAndCacheTle(context)
                        isFetching = false
                        res.onSuccess { (entries, ts) ->
                            catalog = entries
                            cacheTimestampMs = ts
                            view.performHapticConfirm()
                        }.onFailure { err ->
                            fetchError = err.message ?: "Failed to download TLEs"
                            view.performHapticReject()
                        }
                    }
                }
            ) {
                if (isFetching) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = CosmicBlue)
                } else {
                    Icon(Icons.Default.CloudDownload, "Update TLEs", tint = CosmicBlue)
                }
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
                headline = if (catalog.isEmpty()) "Download Orbital Data Once" else "Forecasting ${catalog.size} Satellites Offline",
                description = "Slide the forecast bar to see where satellites will be in the next 24 hours and find the best window for high accuracy (lowest PDOP).",
                accentColor = CosmicBlueLight
            )

            // ── Orbital Catalog Cache Status ─────────────────────────────────
            LabSectionCard("Orbital Data Cache", origin = DataOrigin.EXTERNAL) {
                val ageHours = if (cacheTimestampMs > 0L) (System.currentTimeMillis() - cacheTimestampMs) / 3_600_000.0 else null
                MonoMetricRow("Catalog Size", if (catalog.isEmpty()) "No TLEs cached" else "${catalog.size} GNSS satellites")
                MonoMetricRow(
                    "Cache Age",
                    if (ageHours == null) "Not Downloaded" else "${"%.1f".format(ageHours)} hours",
                    valueColor = when {
                        ageHours == null -> SignalFair
                        ageHours > 72.0 -> SignalFair
                        else -> SignalExcellent
                    }
                )
                if (fetchError != null) {
                    Text(fetchError!!, style = MaterialTheme.typography.labelSmall, color = SignalPoor)
                }
                if (catalog.isEmpty()) {
                    Button(
                        onClick = {
                            isFetching = true
                            fetchError = null
                            scope.launch {
                                val res = fetchAndCacheTle(context)
                                isFetching = false
                                res.onSuccess { (entries, ts) ->
                                    catalog = entries
                                    cacheTimestampMs = ts
                                    view.performHapticConfirm()
                                }.onFailure { err ->
                                    fetchError = err.message ?: "Network error"
                                    view.performHapticReject()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Download CelesTrak GNSS TLEs")
                    }
                }
            }

            if (catalog.isNotEmpty()) {
                // ── Time Offset Slider ───────────────────────────────────────
                LabSectionCard("Forecast Time: +${offsetHours.toInt()}h (${timeFmt.format(Date((targetUnixSec * 1000).toLong()))})", origin = DataOrigin.DERIVED) {
                    Slider(
                        value = offsetHours,
                        onValueChange = { offsetHours = it },
                        valueRange = 0f..24f,
                        steps = 23
                    )
                    MonoMetricRow("Predicted Visible (≥5°)", "${predicted.size} satellites")
                    MonoMetricRow("Estimated PDOP", predictedPdop?.let { "${"%.2f".format(it)}" } ?: "—")
                    if (bestWindow != null) {
                        MonoMetricRow(
                            "Optimal 24h Window",
                            "+${bestWindow.first}h (PDOP ${"%.2f".format(bestWindow.second)})",
                            valueColor = SignalExcellent
                        )
                    }
                }

                // ── Predicted Sky Map ────────────────────────────────────────
                LabSectionCard("Predicted Sky Plot", origin = DataOrigin.DERIVED) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(SpaceDark, RoundedCornerShape(14.dp))
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val maxR = size.minDimension / 2f - 16f
                        listOf(1f, 0.66f, 0.33f).forEach { frac ->
                            drawCircle(SpaceDivider, maxR * frac, Offset(cx, cy), style = Stroke(1.5f))
                        }
                        drawLine(SpaceDivider, Offset(cx, cy - maxR), Offset(cx, cy + maxR), 1f)
                        drawLine(SpaceDivider, Offset(cx - maxR, cy), Offset(cx + maxR, cy), 1f)

                        predicted.forEach { sat ->
                            val r = maxR * (1f - sat.elevationDeg.toFloat() / 90f)
                            val azRad = Math.toRadians(sat.azimuthDeg)
                            val x = cx + r * sin(azRad).toFloat()
                            val y = cy - r * cos(azRad).toFloat()
                            drawCircle(sat.constellation.color(), 5.5f, Offset(x, y))
                        }
                    }
                }

                // ── 24h PDOP Forecast Chart ──────────────────────────────────
                LabSectionCard("24-Hour PDOP Forecast", origin = DataOrigin.DERIVED) {
                    LabLineChart(pdopForecast.map { it.second.coerceAtMost(10f) }, CosmicBlue, "PDOP")
                }

                // ── Predicted Satellites List ────────────────────────────────
                LabSectionCard("Visible Satellites (Top 12)", origin = DataOrigin.DERIVED) {
                    predicted.sortedByDescending { it.elevationDeg }.take(12).forEach { sat ->
                        MonoMetricRow(
                            sat.name,
                            "El ${"%.0f".format(sat.elevationDeg)}° • Az ${"%.0f".format(sat.azimuthDeg)}°"
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private suspend fun fetchAndCacheTle(context: Context): Result<Pair<List<TleEntry>, Long>> = withContext(Dispatchers.IO) {
    runCatching {
        val client = OkHttpClient()
        val req = Request.Builder().url(CELESTRAK_GNSS_URL).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) error("HTTP ${resp.code}")
        val body = resp.body?.string() ?: error("Empty TLE response")
        val entries = OrbitalPropagator.parseTleCatalog(body)
        if (entries.isEmpty()) error("No valid TLE entries parsed")
        val file = File(context.filesDir, TLE_CACHE_FILENAME)
        file.writeText(body)
        Pair(entries, file.lastModified())
    }
}
