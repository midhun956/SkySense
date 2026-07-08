package com.skysense.app.data.model

/**
 * Supported GNSS constellations.
 */
enum class ConstellationType(val displayName: String, val shortName: String) {
    GPS("GPS", "GPS"),
    GLONASS("GLONASS", "GLO"),
    GALILEO("Galileo", "GAL"),
    BEIDOU("BeiDou", "BDS"),
    QZSS("QZSS", "QZS"),
    IRNSS("IRNSS", "IRN"),
    SBAS("SBAS", "SBS"),
    UNKNOWN("Unknown", "UNK")
}

/**
 * Immutable snapshot of a single satellite's state.
 */
data class SatelliteInfo(
    val svid: Int,
    val constellation: ConstellationType,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val cn0DbHz: Float,          // Carrier-to-noise density (signal strength)
    val usedInFix: Boolean,
    val hasCarrierFrequency: Boolean = false,
    val carrierFrequencyHz: Float = 0f
) {
    /** Returns "L1", "L5", or "L1/L5" based on carrier frequency. */
    val frequencyBandLabel: String get() {
        if (!hasCarrierFrequency) return "Unknown"
        return when {
            carrierFrequencyHz in 1_574_000_000f..1_577_000_000f -> "L1"
            carrierFrequencyHz in 1_175_000_000f..1_177_000_000f -> "L5"
            carrierFrequencyHz in 1_227_000_000f..1_228_000_000f -> "L2"
            carrierFrequencyHz in 1_598_000_000f..1_606_000_000f -> "L1"   // GLONASS L1
            carrierFrequencyHz in 1_242_000_000f..1_250_000_000f -> "L2"   // GLONASS L2
            carrierFrequencyHz in 1_561_000_000f..1_563_000_000f -> "B1"   // BeiDou
            carrierFrequencyHz in 1_575_000_000f..1_576_000_000f -> "E1"   // Galileo
            else -> "%.1f MHz".format(carrierFrequencyHz / 1_000_000f)
        }
    }

    /** Signal quality tier based on C/N₀ */
    val signalQuality: SignalQuality get() = when {
        cn0DbHz >= 40f -> SignalQuality.EXCELLENT
        cn0DbHz >= 30f -> SignalQuality.GOOD
        cn0DbHz >= 20f -> SignalQuality.FAIR
        else -> SignalQuality.POOR
    }
}

enum class SignalQuality(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor")
}

/**
 * A complete snapshot of the current GNSS state.
 */
data class GnssSnapshot(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val horizontalAccuracy: Float = 0f,
    val verticalAccuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val constellationsUsed: Set<ConstellationType> = emptySet(),
    val pdop: Float = 0f,
    val hdop: Float = 0f,
    val vdop: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val hasValidFix: Boolean = false
)

/**
 * Prompt profile for Gemini AI requests.
 */
enum class PromptProfile(val displayName: String, val description: String) {
    BEGINNER("Beginner", "Simple language, like explaining to a child"),
    STUDENT("Student", "Suitable for engineering students"),
    EXPERT("Expert", "Full technical GNSS terminology"),
    FUN_FACTS("Fun Facts", "Include historical and scientific facts"),
    CUSTOM("Custom", "Your own custom prompt style")
}
