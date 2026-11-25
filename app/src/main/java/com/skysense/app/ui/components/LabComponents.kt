package com.skysense.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skysense.app.ui.theme.*
import com.skysense.app.util.performHapticSegmentTick

enum class DataOrigin(val label: String, val color: Color) {
    HARDWARE("Direct Hardware", SignalExcellent),
    DERIVED("Derived", CosmicBlue),
    EXTERNAL("External Data", GalileoColor),
    EXPERIMENTAL("Experimental", QzssColor)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabInstrumentScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = StarWhite)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = DimGrey)
                        }
                    }
                },
                navigationIcon = {
                    val view = LocalView.current
                    IconButton(onClick = {
                        view.performHapticSegmentTick()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MoonGrey)
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceDeep)
            )
        },
        containerColor = SpaceBlack,
        content = content
    )
}

@Composable
fun OriginBadge(origin: DataOrigin) {
    Surface(
        shape = RoundedCornerShape(50),
        color = origin.color.copy(alpha = 0.14f)
    ) {
        Text(
            text = origin.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = origin.color,
            fontSize = 10.sp
        )
    }
}

@Composable
fun LabSectionCard(
    title: String,
    origin: DataOrigin? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MoonGrey, fontWeight = FontWeight.SemiBold)
                if (origin != null) OriginBadge(origin)
            }
            content()
        }
    }
}

@Composable
fun PlainSummaryBanner(
    headline: String,
    description: String,
    accentColor: Color = CosmicBlue
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MoonGrey
            )
        }
    }
}

@Composable
fun QuickStatRow(
    stats: List<Triple<String, String, Color>>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        stats.forEach { (label, value, color) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = SpaceCard
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = DimGrey, maxLines = 1)
                    Text(
                        value,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = color,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun MonoMetricRow(
    label: String,
    value: String,
    valueColor: Color = StarWhite,
    hint: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (value.length > 24) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = DimGrey
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = valueColor
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = DimGrey,
                    modifier = Modifier.weight(0.48f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = valueColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(0.52f)
                )
            }
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = DimGrey.copy(alpha = 0.7f), fontSize = 10.sp)
        }
    }
}

@Composable
fun ExpandableTechNote(
    title: String,
    explanation: String
) {
    var expanded by remember { mutableStateOf(false) }
    val view = LocalView.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticSegmentTick()
                expanded = !expanded
            },
        shape = RoundedCornerShape(14.dp),
        color = SpaceDark
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, null, tint = CosmicBlue, modifier = Modifier.size(16.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = CosmicBlueLight,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = DimGrey,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MoonGrey,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun LabLineChart(
    values: List<Float>,
    lineColor: Color,
    unit: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(110.dp),
    showZeroLine: Boolean = false
) {
    if (values.size < 2) {
        Box(
            modifier = modifier
                .background(SpaceDark, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Collecting samples…", style = MaterialTheme.typography.bodySmall, color = DimGrey)
        }
        return
    }
    val minVal = values.minOrNull() ?: 0f
    val maxVal = values.maxOrNull() ?: 1f
    val span = (maxVal - minVal).takeIf { it > 1e-6f } ?: 1f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Min: ${"%.2f".format(minVal)} $unit",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = DimGrey
            )
            Text(
                "Max: ${"%.2f".format(maxVal)} $unit",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = DimGrey
            )
        }
        Canvas(
            modifier = modifier
                .background(SpaceDark, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            val w = size.width
            val h = size.height
            if (showZeroLine && minVal <= 0f && maxVal >= 0f) {
                val yZero = h - ((0f - minVal) / span) * h
                drawLine(
                    color = SpaceDivider,
                    start = Offset(0f, yZero),
                    end = Offset(w, yZero),
                    strokeWidth = 1.5f
                )
            }
            val path = Path()
            values.forEachIndexed { idx, v ->
                val x = (idx.toFloat() / (values.size - 1)) * w
                val y = h - ((v - minVal) / span) * h
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }
    }
}
