package com.skysense.app.ui.screens.constellation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skysense.app.ui.theme.*

data class ConstellationInfo(
    val name: String,
    val shortName: String,
    val country: String,
    val flag: String,
    val color: Color,
    val established: String,
    val satellites: Int,
    val coverage: String,
    val purpose: String,
    val history: String,
    val funFacts: List<String>
)

private val constellations = listOf(
    ConstellationInfo(
        name = "GPS", shortName = "GPS", country = "United States", flag = "🇺🇸",
        color = GpsColor,
        established = "1994",
        satellites = 31,
        coverage = "Global",
        purpose = "Navigation, timing, and positioning for civilian and military use.",
        history = "The Global Positioning System (GPS) was developed by the US Department of Defense during the 1970s and became fully operational in 1994. Originally designed for military navigation, it was made available for civilian use after Korean Air Lines Flight 007 was shot down in 1983 due to navigational errors.",
        funFacts = listOf(
            "GPS satellites travel at about 14,000 km/h",
            "Each satellite completes 2 orbits per day at ~20,200 km altitude",
            "GPS accuracy requires Einstein's theory of relativity corrections — without them, GPS would drift by ~11 km per day",
            "GPS is the most widely used satellite navigation system on Earth"
        )
    ),
    ConstellationInfo(
        name = "Galileo", shortName = "GAL", country = "European Union", flag = "🇪🇺",
        color = GalileoColor,
        established = "2016",
        satellites = 30,
        coverage = "Global",
        purpose = "Civilian-controlled global navigation with high accuracy.",
        history = "Galileo is the European Union's independent satellite navigation system, developed as a civilian alternative to GPS and GLONASS. Full operational capability was reached in 2016. It is the only global GNSS system under purely civilian control.",
        funFacts = listOf(
            "Galileo provides 1-meter accuracy in open sky",
            "It uses the highest orbital altitude of any GNSS (23,222 km)",
            "Each Galileo satellite has 2 passive hydrogen maser atomic clocks",
            "Galileo has a dedicated Search and Rescue (SAR) service built in"
        )
    ),
    ConstellationInfo(
        name = "GLONASS", shortName = "GLO", country = "Russia", flag = "🇷🇺",
        color = GlonassColor,
        established = "1993",
        satellites = 24,
        coverage = "Global",
        purpose = "Navigation and positioning for Russian and international users.",
        history = "GLONASS (Globalnaya Navigatsionnaya Sputnikovaya Sistema) was developed by the Soviet Union during the Cold War as an alternative to GPS. It became fully operational in 1993, briefly declined after the Soviet collapse, and was fully restored by 2011 under Russian government investment.",
        funFacts = listOf(
            "GLONASS uses frequency-division multiplexing, unlike GPS which uses code-division",
            "Russian aircraft are legally required to use GLONASS over GPS",
            "Combining GLONASS with GPS significantly improves accuracy in urban canyons",
            "GLONASS satellites orbit at 19,100 km altitude in 3 orbital planes"
        )
    ),
    ConstellationInfo(
        name = "BeiDou", shortName = "BDS", country = "China", flag = "🇨🇳",
        color = BeiDouColor,
        established = "2020",
        satellites = 49,
        coverage = "Global",
        purpose = "Navigation, precise timing, and short-message communication.",
        history = "BeiDou (literally 'North Star' in Chinese) started as a regional system serving China and neighboring countries. BeiDou-3, completed in 2020, is now a global system with the largest constellation of any GNSS. Uniquely, it includes a two-way messaging service.",
        funFacts = listOf(
            "BeiDou has the most satellites of any constellation — over 55 in orbit",
            "It includes a unique short-message service allowing users to send 1000-character texts",
            "BeiDou's GEO satellites provide enhanced accuracy over the Asia-Pacific region",
            "Fishermen in China have used BeiDou messaging for ocean safety since the 2000s"
        )
    ),
    ConstellationInfo(
        name = "QZSS", shortName = "QZS", country = "Japan", flag = "🇯🇵",
        color = QzssColor,
        established = "2018",
        satellites = 7,
        coverage = "Japan & Oceania",
        purpose = "Augmentation of GPS with enhanced coverage over Japan.",
        history = "The Quasi-Zenith Satellite System (QZSS) is Japan's regional augmentation system designed to improve GPS accuracy and availability over Japan, particularly in urban canyons and mountainous terrain. At least one satellite is always directly overhead Japan (hence 'quasi-zenith').",
        funFacts = listOf(
            "QZSS satellites follow a figure-8 ground track over Japan",
            "At least one QZSS satellite is always above 70° elevation over Japan",
            "QZSS was designed specifically to work in Japan's narrow urban streets",
            "It supports sub-centimeter accuracy when combined with ground-based corrections"
        )
    ),
    ConstellationInfo(
        name = "IRNSS", shortName = "IRN", country = "India", flag = "🇮🇳",
        color = IrnssColor,
        established = "2018",
        satellites = 7,
        coverage = "Regional (India and up to 1,500 km around it)",
        purpose = "Regional satellite navigation system for civilian and strategic use.",
        history = "The Indian Regional Navigation Satellite System (IRNSS), with an operational name of NavIC (Navigation with Indian Constellation), is an autonomous regional satellite navigation system developed by the Indian Space Research Organisation (ISRO). It provides accurate real-time positioning and timing services covering India and a region extending 1,500 km around it.",
        funFacts = listOf(
            "NavIC stands for 'Navigation with Indian Constellation'",
            "It consists of both geostationary and geosynchronous satellites",
            "It operates in the L5 and S frequency bands",
            "NavIC is designed to provide position accuracy better than 20 meters"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstellationExplorerScreen() {
    val pagerState = rememberPagerState { constellations.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Constellation Explorer", color = StarWhite) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceDeep)
            )
        },
        containerColor = SpaceBlack
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Tab row
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = SpaceDeep,
                contentColor = StarWhite,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(constellations[pagerState.currentPage].color)
                        )
                    }
                },
                edgePadding = 0.dp
            ) {
                constellations.forEachIndexed { index, info ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                "${info.flag} ${info.shortName}",
                                color = if (pagerState.currentPage == index) info.color else DimGrey,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ConstellationPage(constellations[page])
            }
        }
    }
}

@Composable
private fun ConstellationPage(info: ConstellationInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SpaceCardElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(info.color.copy(alpha = 0.12f), Color.Transparent))
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(info.flag, style = MaterialTheme.typography.displayMedium.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified))
                        Column {
                            Text(info.name, style = MaterialTheme.typography.headlineMedium, color = info.color)
                            Text(info.country, style = MaterialTheme.typography.bodyLarge, color = MoonGrey)
                        }
                    }
                    Text(info.purpose, style = MaterialTheme.typography.bodyMedium, color = MoonGrey)
                }
            }
        }

        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Established", info.established, info.color, Modifier.weight(1f))
            StatCard("Satellites", "${info.satellites}", info.color, Modifier.weight(1f))
            StatCard("Coverage", info.coverage, info.color, Modifier.weight(1f))
        }

        // History
        InfoSection("History", Icons.Default.HistoryEdu, info.history, info.color)

        // Fun facts
        FunFactsCard(info.funFacts, info.color)

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = DimGrey)
        }
    }
}

@Composable
private fun InfoSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = StarWhite)
            }
            Text(content, style = MaterialTheme.typography.bodyMedium, color = MoonGrey)
        }
    }
}

@Composable
private fun FunFactsCard(facts: List<String>, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Lightbulb, null, tint = GalileoColor, modifier = Modifier.size(18.dp))
                Text("Fun Facts", style = MaterialTheme.typography.titleSmall, color = StarWhite)
            }
            facts.forEach { fact ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Text(fact, style = MaterialTheme.typography.bodyMedium, color = MoonGrey)
                }
            }
        }
    }
}
