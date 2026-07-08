package com.skysense.app.ui.screens.skymap

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skysense.app.data.model.ConstellationType
import com.skysense.app.data.model.SatelliteInfo
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.data.sensor.CompassManager
import com.skysense.app.ui.screens.dashboard.color
import com.skysense.app.ui.theme.*
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyMapScreen(
    repository: GnssRepository,
    compassManager: CompassManager,
    onSatelliteClick: (Int, String, String) -> Unit
) {
    val viewModel: SkyMapViewModel = viewModel(factory = SkyMapViewModel.Factory(repository, compassManager))
    val satellites by viewModel.satellites.collectAsStateWithLifecycle()
    val selected by viewModel.selectedSatellite.collectAsStateWithLifecycle()
    val receiving by viewModel.isReceivingUpdates.collectAsStateWithLifecycle()
    val heading by viewModel.heading.collectAsStateWithLifecycle()
    var filterType by remember { mutableStateOf<String?>(null) }

    val animatedHeading by animateFloatAsState(
        targetValue = heading,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "HeadingAnim"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sky Map", style = MaterialTheme.typography.titleLarge, color = StarWhite) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceDeep)
            )
        },
        containerColor = SpaceBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sky plot takes most of the screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                if (satellites.isEmpty()) {
                    EmptySkyState(receiving)
                } else {
                    SkyPlot(
                        satellites = satellites,
                        selected = selected,
                        heading = animatedHeading,
                        onSatelliteTap = { sat ->
                            viewModel.selectSatellite(sat)
                            onSatelliteClick(sat.svid, sat.constellation.name, sat.frequencyBandLabel)
                        }
                    )
                }
            }

            // Constellation legend
            ConstellationLegend()

            // Selected satellite mini-card
            selected?.let { sat ->
                SelectedSatCard(sat = sat, onDismiss = { viewModel.selectSatellite(null) })
            }

            // Satellite count chips
            SatelliteCountRow(satellites = satellites, onFilterClick = { filterType = it })

            Spacer(Modifier.height(80.dp))
        }

        if (filterType != null) {
            SatelliteListBottomSheet(
                filterType = filterType!!,
                satellites = satellites,
                onDismiss = { filterType = null },
                onSatelliteClick = { sat ->
                    filterType = null
                    onSatelliteClick(sat.svid, sat.constellation.name, sat.frequencyBandLabel)
                }
            )
        }
    }
}

@Composable
private fun SkyPlot(
    satellites: List<SatelliteInfo>,
    selected: SatelliteInfo?,
    heading: Float,
    onSatelliteTap: (SatelliteInfo) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(Pair(0f, 0f)) }
    val satPositions = remember(satellites, canvasSize) {
        satellites.map { sat ->
            val (cx, cy) = canvasSize
            val pos = polarToCartesian(
                azimuth = sat.azimuthDegrees,
                elevation = sat.elevationDegrees,
                centerX = cx / 2f,
                centerY = cy / 2f,
                radius = minOf(cx, cy) / 2f * 0.9f
            )
            sat to pos
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "scanAngle"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(Color(0xFF090D18))
            .graphicsLayer { rotationZ = -heading }
            .pointerInput(satPositions) {
                detectTapGestures { offset ->
                    satPositions.minByOrNull { (_, pos) ->
                        val (px, py) = pos
                        sqrt((offset.x - px).pow(2) + (offset.y - py).pow(2))
                    }?.let { (sat, pos) ->
                        val (px, py) = pos
                        val dist = sqrt((offset.x - px).pow(2) + (offset.y - py).pow(2))
                        if (dist < 36f) onSatelliteTap(sat)
                    }
                }
            }
    ) {
        canvasSize = Pair(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = minOf(size.width, size.height) / 2f * 0.9f

        drawSkyBackground(cx, cy, maxR, textMeasurer, scanAngle, heading)
        drawSatellites(satPositions, selected, textMeasurer, heading)
    }
}

private fun DrawScope.drawSkyBackground(
    cx: Float, cy: Float, maxR: Float,
    textMeasurer: TextMeasurer,
    scanAngle: Float,
    heading: Float
) {
    // Elevation rings: 0°, 30°, 60°, 90° horizon
    val rings = listOf(0f to "0°", 30f to "30°", 60f to "60°", 90f to "")
    rings.forEach { (elevation, label) ->
        val r = maxR * (1f - elevation / 90f)
        drawCircle(
            color = when {
                elevation == 0f -> Color(0xFF2A3A5A)
                else -> Color(0xFF1A2A3A)
            },
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = if (elevation == 0f) 2f else 1f)
        )
        if (label.isNotEmpty()) {
            val layout = textMeasurer.measure(
                AnnotatedString(label),
                style = TextStyle(fontSize = 9.sp, color = Color(0xFF3A5A8A))
            )
            val centerOffset = Offset(cx + r - layout.size.width / 2f - 4f, cy)
            rotate(heading, pivot = centerOffset) {
                drawText(layout, topLeft = Offset(centerOffset.x - layout.size.width / 2f, centerOffset.y - layout.size.height / 2f))
            }
        }
    }

    // Cardinal direction labels
    val cardinals = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
    cardinals.forEach { (dir, angle) ->
        val rad = Math.toRadians(angle.toDouble() - 90.0)
        val lx = cx + cos(rad).toFloat() * (maxR + 16f)
        val ly = cy + sin(rad).toFloat() * (maxR + 16f)
        val layout = textMeasurer.measure(
            AnnotatedString(dir),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A8ACA))
        )
        val textCenter = Offset(lx, ly)
        rotate(heading, pivot = textCenter) {
            drawText(layout, topLeft = Offset(textCenter.x - layout.size.width / 2f, textCenter.y - layout.size.height / 2f))
        }
    }

    // Scanning sweep line
    val sweepRad = Math.toRadians(scanAngle.toDouble() - 90.0)
    drawLine(
        color = Color(0x222DD4A0),
        start = Offset(cx, cy),
        end = Offset(cx + cos(sweepRad).toFloat() * maxR, cy + sin(sweepRad).toFloat() * maxR),
        strokeWidth = 60f
    )
    drawLine(
        color = Color(0x552DD4A0),
        start = Offset(cx, cy),
        end = Offset(cx + cos(sweepRad).toFloat() * maxR, cy + sin(sweepRad).toFloat() * maxR),
        strokeWidth = 2f
    )

    // Center dot
    drawCircle(color = Color(0xFF3A5A8A), radius = 4f, center = Offset(cx, cy))
}

private fun DrawScope.drawSatellites(
    satPositions: List<Pair<SatelliteInfo, Pair<Float, Float>>>,
    selected: SatelliteInfo?,
    textMeasurer: TextMeasurer,
    heading: Float
) {
    satPositions.forEach { (sat, pos) ->
        val (px, py) = pos
        val color = sat.constellation.color()
        val radius = 10f + (sat.cn0DbHz / 60f) * 8f
        val alpha = if (sat.usedInFix) 1f else 0.45f

        // Outer glow for used-in-fix
        if (sat.usedInFix) {
            drawCircle(
                color = color.copy(alpha = 0.2f),
                radius = radius + 8f,
                center = Offset(px, py)
            )
        }

        // Selection ring
        if (selected?.svid == sat.svid && selected.constellation == sat.constellation) {
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = radius + 5f,
                center = Offset(px, py),
                style = Stroke(width = 2f)
            )
        }

        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius,
            center = Offset(px, py)
        )

        // PRN label
        val label = "${sat.constellation.shortName}-${sat.svid}"
        val layout = textMeasurer.measure(
            AnnotatedString(label),
            style = TextStyle(fontSize = 8.sp, color = Color.White.copy(alpha = alpha))
        )
        val textCenter = Offset(px, py + radius + 2f + layout.size.height / 2f)
        rotate(heading, pivot = textCenter) {
            drawText(
                layout,
                topLeft = Offset(textCenter.x - layout.size.width / 2f, textCenter.y - layout.size.height / 2f)
            )
        }
    }
}

private fun polarToCartesian(
    azimuth: Float, elevation: Float,
    centerX: Float, centerY: Float, radius: Float
): Pair<Float, Float> {
    val r = radius * (1f - elevation / 90f)
    val angleRad = Math.toRadians((azimuth - 90.0))
    return Pair(
        (centerX + cos(angleRad).toFloat() * r),
        (centerY + sin(angleRad).toFloat() * r)
    )
}

private fun ConstellationType.color(): Color = when (this) {
    ConstellationType.GPS -> GpsColor
    ConstellationType.GALILEO -> GalileoColor
    ConstellationType.GLONASS -> GlonassColor
    ConstellationType.BEIDOU -> BeiDouColor
    ConstellationType.QZSS -> QzssColor
    ConstellationType.IRNSS -> IrnssColor
    ConstellationType.SBAS -> SbasColor
    ConstellationType.UNKNOWN -> UnknownSatColor
}

@Composable
private fun ConstellationLegend() {
    LazyRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(listOf(
            ConstellationType.GPS,
            ConstellationType.GALILEO,
            ConstellationType.GLONASS,
            ConstellationType.BEIDOU,
            ConstellationType.QZSS,
            ConstellationType.IRNSS
        )) { c ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.color()))
                Text(c.displayName, style = MaterialTheme.typography.labelSmall, color = MoonGrey)
            }
        }
    }
}

@Composable
private fun SelectedSatCard(sat: SatelliteInfo, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCardElevated)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(sat.constellation.color()))
            Column(Modifier.weight(1f)) {
                Text(
                    "${sat.constellation.displayName} PRN ${sat.svid}",
                    style = MaterialTheme.typography.titleSmall,
                    color = StarWhite
                )
                Text(
                    "El: ${"%.0f".format(sat.elevationDegrees)}°  Az: ${"%.0f".format(sat.azimuthDegrees)}°  C/N₀: ${"%.1f".format(sat.cn0DbHz)} dB-Hz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoonGrey
                )
            }
            Text(
                if (sat.usedInFix) "In Fix" else "Tracking",
                style = MaterialTheme.typography.labelSmall,
                color = if (sat.usedInFix) SignalExcellent else DimGrey
            )
        }
    }
}

@Composable
private fun SatelliteCountRow(satellites: List<SatelliteInfo>, onFilterClick: (String) -> Unit) {
    val used = satellites.count { it.usedInFix }
    val visible = satellites.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SatCountChip("Used in Fix", used.toString(), SignalExcellent, Modifier.weight(1f)) { onFilterClick("used") }
        SatCountChip("Tracking", (visible - used).toString(), GpsColor, Modifier.weight(1f)) { onFilterClick("tracking") }
        SatCountChip("Visible", visible.toString(), MoonGrey, Modifier.weight(1f)) { onFilterClick("visible") }
    }
}

@Composable
private fun SatCountChip(label: String, count: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = SpaceCard
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = DimGrey)
        }
    }
}

@Composable
private fun EmptySkyState(receiving: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Satellite, contentDescription = null, tint = DimGrey, modifier = Modifier.size(48.dp))
            Text(
                if (receiving) "Waiting for satellite data…" else "Searching for GPS signal…",
                style = MaterialTheme.typography.bodyLarge,
                color = DimGrey
            )
            Text(
                "Move to an open area with a clear sky view",
                style = MaterialTheme.typography.bodySmall,
                color = DimGrey.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatelliteListBottomSheet(
    filterType: String,
    satellites: List<SatelliteInfo>,
    onDismiss: () -> Unit,
    onSatelliteClick: (SatelliteInfo) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filteredSats = remember(filterType) {
        when (filterType) {
            "used" -> satellites.filter { it.usedInFix }
            "tracking" -> satellites.filter { !it.usedInFix && it.cn0DbHz > 0 }
            "visible" -> satellites
            else -> satellites
        }
    }
    val title = when (filterType) {
        "used" -> "Satellites Used in Fix"
        "tracking" -> "Satellites Tracking"
        "visible" -> "All Visible Satellites"
        else -> "Satellites"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SpaceDeep,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DimGrey) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = StarWhite,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredSats) { sat ->
                    Surface(
                        onClick = { onSatelliteClick(sat) },
                        shape = RoundedCornerShape(12.dp),
                        color = SpaceCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(sat.constellation.color()))
                            Text(
                                text = "${sat.constellation.name} ${sat.svid}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StarWhite,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            // Frequency Band Tag
                            Surface(shape = RoundedCornerShape(4.dp), color = CosmicBlueDark) {
                                Text(
                                    text = sat.frequencyBandLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StarWhite,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            // SNR
                            Text(
                                text = "${"%.1f".format(sat.cn0DbHz)} dB",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (sat.cn0DbHz > 30f) SignalExcellent else MoonGrey
                            )
                        }
                    }
                }
            }
        }
    }
}
