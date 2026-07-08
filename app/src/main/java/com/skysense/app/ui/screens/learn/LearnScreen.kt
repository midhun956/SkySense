package com.skysense.app.ui.screens.learn

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skysense.app.ui.theme.*

data class GlossaryTerm(
    val term: String,
    val abbreviation: String = "",
    val category: String,
    val beginnerExplanation: String,
    val technicalExplanation: String
)

private val glossaryTerms = listOf(
    GlossaryTerm(
        term = "PDOP", abbreviation = "PDOP",
        category = "Accuracy",
        beginnerExplanation = "PDOP tells you how spread out the GPS satellites are in the sky. Imagine you're trying to figure out where you are using landmarks — if all the landmarks are in one direction, it's much harder to pinpoint exactly where you are. PDOP measures this. Lower is better.",
        technicalExplanation = "Position Dilution of Precision (PDOP) is the geometric factor amplifying position errors. It's derived from the trace of the covariance matrix (HᵀH)⁻¹, where H is the design matrix. PDOP < 2 is considered excellent; > 6 indicates poor geometry."
    ),
    GlossaryTerm(
        term = "HDOP", abbreviation = "HDOP",
        category = "Accuracy",
        beginnerExplanation = "HDOP is like PDOP, but just for your left-right and forward-back position on a map. It tells you how accurate your 2D location (latitude and longitude) is. Lower is better.",
        technicalExplanation = "Horizontal Dilution of Precision (HDOP) quantifies geometric amplification in the horizontal plane. Computed from the first two diagonal elements of (HᵀH)⁻¹, where H is the unit vector design matrix. Multiplied by UERE to yield horizontal accuracy estimates."
    ),
    GlossaryTerm(
        term = "VDOP", abbreviation = "VDOP",
        category = "Accuracy",
        beginnerExplanation = "VDOP is like HDOP, but just for your altitude — how high up you are. GPS is usually less accurate for altitude than for horizontal position, so VDOP is often higher.",
        technicalExplanation = "Vertical Dilution of Precision (VDOP) quantifies geometric amplification in the vertical axis. GPS systems typically exhibit VDOP values 1.5–2× higher than HDOP due to the inability to observe satellites below the horizon."
    ),
    GlossaryTerm(
        term = "C/N₀", abbreviation = "C/N₀",
        category = "Signal",
        beginnerExplanation = "C/N₀ (pronounced 'C-N-naught') is a measure of how strong and clean the GPS signal from a satellite is. Think of it like a radio signal — the stronger it is, the less static, and the better your GPS will work. 40+ dB-Hz is excellent.",
        technicalExplanation = "Carrier-to-Noise Density Ratio (C/N₀) is the ratio of the received carrier signal power to the noise power density, expressed in dB-Hz. It is independent of bandwidth and is the preferred signal quality metric in GNSS. Typical values range from 20 to 50 dB-Hz. C/N₀ = C/kT₀B where k is Boltzmann's constant."
    ),
    GlossaryTerm(
        term = "L1 Frequency", abbreviation = "L1",
        category = "Signals",
        beginnerExplanation = "L1 is the main radio frequency that GPS satellites use to talk to your phone. It's like a specific TV channel — 1575.42 MHz. Almost all GPS devices listen on L1.",
        technicalExplanation = "L1 (1575.42 MHz) is the primary GPS civil signal frequency, carrying the C/A code (Coarse/Acquisition) at 1.023 Mcps and the P(Y) military code at 10.23 Mcps. It is the dominant signal for single-frequency civilian receivers. GLONASS L1 is centered around 1602 MHz using FDMA."
    ),
    GlossaryTerm(
        term = "L5 Frequency", abbreviation = "L5",
        category = "Signals",
        beginnerExplanation = "L5 is a newer, more powerful GPS signal. Phones that can hear both L1 and L5 can figure out your location much more accurately — sometimes to within a meter! Many modern flagship smartphones now support L5.",
        technicalExplanation = "L5 (1176.45 MHz) is the third GPS civil signal, protected by aeronautical radionavigation regulations. It features higher power, a wider bandwidth (10.23 Mcps), and improved data integrity. Dual-frequency L1/L5 receivers can eliminate ionospheric delay errors, a major error source, improving accuracy from ~3m to sub-meter."
    ),
    GlossaryTerm(
        term = "Elevation Angle", abbreviation = "",
        category = "Geometry",
        beginnerExplanation = "The elevation angle tells you how high in the sky a satellite is. If it's at 0°, it's on the horizon. At 90°, it's directly above your head. Satellites above 15° usually give the best signal.",
        technicalExplanation = "Elevation angle (α) is the angle between the local horizontal plane and the line of sight to the satellite. Low-elevation satellites (<15°) are subject to increased tropospheric and multipath errors. A mask angle of 10–15° is commonly applied in GNSS processing to exclude low-elevation signals."
    ),
    GlossaryTerm(
        term = "Azimuth", abbreviation = "",
        category = "Geometry",
        beginnerExplanation = "Azimuth is the compass direction to a satellite, measured clockwise from North. 0° = North, 90° = East, 180° = South, 270° = West. On the sky map, satellites appear in the direction given by their azimuth.",
        technicalExplanation = "Azimuth (A) is the horizontal angle measured clockwise from geographic North to the satellite's ground projection. Together with elevation, it defines the satellite's position in topocentric coordinates. It is used to transform satellite coordinates from Earth-Centered Earth-Fixed (ECEF) to local East-North-Up (ENU) frames."
    ),
    GlossaryTerm(
        term = "Trilateration", abbreviation = "",
        category = "Fundamentals",
        beginnerExplanation = "GPS doesn't use triangulation (measuring angles) — it uses trilateration (measuring distances). Each satellite tells your phone how far away it is, and by knowing the distance from at least 4 satellites, your phone can calculate exactly where you are.",
        technicalExplanation = "GNSS positioning uses time-of-arrival (TOA) trilateration. Each satellite broadcasts its position and transmission time. The receiver measures pseudoranges (biased ranges including clock error) from ≥4 satellites to solve 4 unknowns: x, y, z position and receiver clock bias (δt). This is solved as a nonlinear least-squares problem iteratively."
    ),
    GlossaryTerm(
        term = "Atomic Clocks", abbreviation = "",
        category = "Fundamentals",
        beginnerExplanation = "GPS satellites have incredibly precise clocks — atomic clocks — that keep time accurately to within a billionth of a second. This precision is essential because GPS calculates distances using how long a signal takes to travel from the satellite to your phone. Even a tiny timing error means a big location error.",
        technicalExplanation = "GPS satellites carry multiple redundant atomic clocks (cesium and rubidium standards) with stability of ~10⁻¹³ seconds/second. Since GNSS ranging uses signal travel time (ρ = c·Δt), a 1 nanosecond timing error produces a 30 cm range error. Ground control stations continuously estimate and upload clock correction parameters to maintain system accuracy."
    ),
    GlossaryTerm(
        term = "Relativity Corrections", abbreviation = "",
        category = "Fundamentals",
        beginnerExplanation = "GPS satellites orbit very fast and very high, which causes their clocks to tick at slightly different rates than clocks on Earth — just like Einstein's theory of relativity predicts! Without corrections for this effect, your GPS would drift by about 11 km per day.",
        technicalExplanation = "Two relativistic effects affect GPS: (1) Special Relativity: satellite velocity (3.87 km/s) causes time dilation of −7.2 μs/day. (2) General Relativity: weaker gravitational potential at satellite altitude causes time to pass faster by +45.9 μs/day. Net effect: +38.4 μs/day. This is pre-corrected by adjusting satellite clock frequency to 10.22999999543 MHz (vs. nominal 10.23 MHz) before launch."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen() {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery) {
        if (searchQuery.isEmpty()) glossaryTerms
        else glossaryTerms.filter {
            it.term.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true) ||
            it.beginnerExplanation.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learn", color = StarWhite) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceDeep)
            )
        },
        containerColor = SpaceBlack
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search glossary…", color = DimGrey) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DimGrey) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null, tint = DimGrey)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CosmicBlue,
                    unfocusedBorderColor = SpaceDivider,
                    focusedTextColor = StarWhite,
                    unfocusedTextColor = StarWhite,
                    cursorColor = CosmicBlue,
                    focusedContainerColor = SpaceCard,
                    unfocusedContainerColor = SpaceCard
                ),
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.term }) { term ->
                    GlossaryTermCard(term)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun GlossaryTermCard(term: GlossaryTerm) {
    var expanded by remember { mutableStateOf(false) }
    var showTechnical by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        term.term,
                        style = MaterialTheme.typography.titleMedium,
                        color = StarWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = CosmicBlue.copy(alpha = 0.12f)
                    ) {
                        Text(
                            term.category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmicBlue
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = DimGrey
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = SpaceDivider)

                    // Toggle tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExplanationTab(
                            label = "Beginner",
                            icon = Icons.Default.EmojiPeople,
                            selected = !showTechnical,
                            onClick = { showTechnical = false },
                            modifier = Modifier.weight(1f)
                        )
                        ExplanationTab(
                            label = "Technical",
                            icon = Icons.Default.Code,
                            selected = showTechnical,
                            onClick = { showTechnical = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AnimatedContent(targetState = showTechnical, label = "explanation") { technical ->
                        Text(
                            if (technical) term.technicalExplanation else term.beginnerExplanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MoonGrey
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplanationTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) CosmicBlue.copy(alpha = 0.15f) else SpaceCardElevated
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = if (selected) CosmicBlue else DimGrey, modifier = Modifier.size(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) CosmicBlue else DimGrey
            )
        }
    }
}
