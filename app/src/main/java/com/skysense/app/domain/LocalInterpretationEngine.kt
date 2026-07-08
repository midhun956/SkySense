package com.skysense.app.domain

import com.skysense.app.data.model.ConstellationType
import com.skysense.app.data.model.GnssSnapshot

/**
 * Pure, offline interpretation engine.
 * Converts raw GNSS metrics into human-readable explanations.
 * No Android dependencies — fully unit-testable.
 */
object LocalInterpretationEngine {

    // ── Accuracy ──────────────────────────────────────────────────────────────

    fun accuracyLabel(horizontalAccuracy: Float): String = when {
        horizontalAccuracy <= 0f -> "Unknown"
        horizontalAccuracy < 3f -> "Exceptional"
        horizontalAccuracy < 5f -> "Excellent"
        horizontalAccuracy < 10f -> "Very good"
        horizontalAccuracy < 20f -> "Good"
        horizontalAccuracy < 50f -> "Fair"
        else -> "Poor"
    }

    fun accuracyExplanation(horizontalAccuracy: Float): String = when {
        horizontalAccuracy <= 0f -> "Accuracy data is not yet available."
        horizontalAccuracy < 3f -> "Location quality is exceptional. Your position is accurate to within a few meters — suitable for precision navigation."
        horizontalAccuracy < 5f -> "Location quality is excellent. Your device has a very strong GPS fix."
        horizontalAccuracy < 10f -> "Location quality is very good. Most applications will work perfectly at this accuracy."
        horizontalAccuracy < 20f -> "Location quality is good. The signal is clear but minor obstructions may be present."
        horizontalAccuracy < 50f -> "Location quality is fair. Accuracy may be reduced due to signal obstructions such as buildings or trees."
        else -> "Location quality is poor. You may be indoors, in a deep urban canyon, or experiencing severe interference. Try moving to an open area."
    }

    // ── PDOP ──────────────────────────────────────────────────────────────────

    fun pdopLabel(pdop: Float): String = when {
        pdop <= 0f -> "Unknown"
        pdop < 1f -> "Ideal"
        pdop < 2f -> "Excellent"
        pdop < 3f -> "Good"
        pdop < 5f -> "Moderate"
        pdop < 8f -> "Fair"
        else -> "Poor"
    }

    fun pdopExplanation(pdop: Float): String = when {
        pdop <= 0f -> "DOP data is not yet available."
        pdop < 2f -> "Satellite geometry is excellent. The satellites are spread well across the sky, providing highly reliable positioning."
        pdop < 3f -> "Satellite geometry is good. The positioning solution is reliable."
        pdop < 5f -> "Satellite geometry is moderate. The positioning solution may have slightly reduced reliability."
        else -> "Satellite geometry is poor. Many satellites may be clustered together, which reduces the accuracy of position calculations. Try moving to an open sky view."
    }

    // ── Constellation summary ─────────────────────────────────────────────────

    fun constellationSummary(constellations: Set<ConstellationType>): String {
        val filtered = constellations.filter {
            it != ConstellationType.UNKNOWN && it != ConstellationType.SBAS
        }
        if (filtered.isEmpty()) return "No constellation data available."
        val names = filtered.joinToString(", ") { it.displayName }
        return when (filtered.size) {
            1 -> "Using $names only."
            2 -> "Using $names for a combined fix."
            else -> "Using $names together for a multi-constellation fix."
        }
    }

    // ── Main overview card ────────────────────────────────────────────────────

    fun buildOverviewCard(snapshot: GnssSnapshot): OverviewCard {
        if (!snapshot.hasValidFix) {
            return OverviewCard(
                headline = "Searching for satellites…",
                summary = "Your device is scanning the sky for GPS satellites. Please move to an open area with a clear view of the sky for best results.",
                accuracyLabel = "No fix",
                pdopLabel = "—",
                constellationSummary = "—",
                signalTier = SignalTier.NONE
            )
        }

        val accLabel = accuracyLabel(snapshot.horizontalAccuracy)
        val tier = signalTier(snapshot.horizontalAccuracy, snapshot.pdop)
        val constellations = snapshot.constellationsUsed.filter {
            it != ConstellationType.UNKNOWN && it != ConstellationType.SBAS
        }
        val constellationNames = constellations.joinToString(", ") { it.displayName }

        val satCount = snapshot.satellitesUsed
        val satPhrase = if (satCount == 1) "1 satellite" else "$satCount satellites"
        val consPhrase = if (constellations.size == 1) constellationNames
                         else if (constellations.isEmpty()) "an unknown constellation"
                         else constellationNames

        val headline = when (tier) {
            SignalTier.EXCELLENT -> "Signal: Excellent"
            SignalTier.GOOD -> "Signal: Good"
            SignalTier.FAIR -> "Signal: Fair"
            SignalTier.POOR -> "Signal: Poor"
            SignalTier.NONE -> "No Fix"
        }

        val accRounded = "%.0f".format(snapshot.horizontalAccuracy)
        val summary = buildString {
            append("Your phone is currently using $satPhrase from $consPhrase. ")
            append("Signal conditions are ${tier.adjective}, ")
            append("resulting in an estimated accuracy of ~${accRounded}m. ")
            if (snapshot.pdop > 0f) append(pdopExplanation(snapshot.pdop))
        }

        return OverviewCard(
            headline = headline,
            summary = summary,
            accuracyLabel = "$accLabel (~${accRounded}m)",
            pdopLabel = "${pdopLabel(snapshot.pdop)} (${if (snapshot.pdop > 0f) "%.1f".format(snapshot.pdop) else "—"})",
            constellationSummary = constellationSummary(snapshot.constellationsUsed),
            signalTier = tier
        )
    }

    fun signalTier(horizontalAccuracy: Float, pdop: Float): SignalTier = when {
        horizontalAccuracy <= 0f -> SignalTier.NONE
        horizontalAccuracy < 5f -> SignalTier.EXCELLENT
        horizontalAccuracy < 15f -> SignalTier.GOOD
        horizontalAccuracy < 30f -> SignalTier.FAIR
        else -> SignalTier.POOR
    }

    // ── Satellite educational explanations ────────────────────────────────────

    fun satelliteExplanation(usedInFix: Boolean, cn0: Float, constellation: String): String {
        val fixStatus = if (usedInFix)
            "This satellite is actively contributing to your location calculation."
        else
            "This satellite is visible but not currently included in your position fix. It may have a weak signal or poor geometry."

        val signalStatus = when {
            cn0 >= 40f -> "The signal strength is excellent (C/N₀ = ${"%.1f".format(cn0)} dB-Hz), indicating a clear line of sight to the satellite."
            cn0 >= 30f -> "The signal strength is good (C/N₀ = ${"%.1f".format(cn0)} dB-Hz)."
            cn0 >= 20f -> "The signal strength is fair (C/N₀ = ${"%.1f".format(cn0)} dB-Hz). Some obstruction may be present."
            cn0 > 0f -> "The signal strength is weak (C/N₀ = ${"%.1f".format(cn0)} dB-Hz). This satellite may be near the horizon or partially blocked."
            else -> "Signal strength data is not available for this satellite."
        }

        return "$fixStatus $signalStatus This is a $constellation satellite."
    }

    // ── Speed ─────────────────────────────────────────────────────────────────

    fun speedDescription(speedMs: Float): String {
        val kmh = speedMs * 3.6f
        return when {
            kmh < 0.5f -> "Stationary"
            kmh < 5f -> "Walking (${"%.1f".format(kmh)} km/h)"
            kmh < 25f -> "Jogging / Cycling (${"%.1f".format(kmh)} km/h)"
            kmh < 120f -> "Driving (${"%.1f".format(kmh)} km/h)"
            else -> "High speed (${"%.1f".format(kmh)} km/h)"
        }
    }
}

data class OverviewCard(
    val headline: String,
    val summary: String,
    val accuracyLabel: String,
    val pdopLabel: String,
    val constellationSummary: String,
    val signalTier: SignalTier
)

enum class SignalTier(val adjective: String) {
    EXCELLENT("excellent"),
    GOOD("good"),
    FAIR("fair"),
    POOR("poor"),
    NONE("unavailable")
}
