package com.skysense.app.data.model

/**
 * Raw GnssClock snapshot with monotonic & wall-clock reception timestamps
 * for accurate sub-second interpolation and system-clock comparison.
 */
data class RawClockData(
    val timeNanos: Long = 0L,
    val hasFullBiasNanos: Boolean = false,
    val fullBiasNanos: Long = 0L,
    val hasBiasNanos: Boolean = false,
    val biasNanos: Double = 0.0,
    val hasBiasUncertaintyNanos: Boolean = false,
    val biasUncertaintyNanos: Double = 0.0,
    val hasDriftNanosPerSecond: Boolean = false,
    val driftNanosPerSecond: Double = 0.0,
    val hasDriftUncertaintyNanosPerSecond: Boolean = false,
    val driftUncertaintyNanosPerSecond: Double = 0.0,
    val hasLeapSecond: Boolean = false,
    val leapSecond: Int = 0,
    val hasTimeUncertaintyNanos: Boolean = false,
    val timeUncertaintyNanos: Double = 0.0,
    val hardwareClockDiscontinuityCount: Int = 0,
    val hasElapsedRealtimeNanos: Boolean = false,
    val clockElapsedRealtimeNanos: Long = 0L,
    val receivedElapsedRealtimeNanos: Long = 0L,
    val receivedSystemTimeMs: Long = 0L,
    val isValid: Boolean = false
)

/**
 * Satellite PVT data when provided by hardware (API 35+).
 */
data class SatellitePvtData(
    val posXMeters: Double = 0.0,
    val posYMeters: Double = 0.0,
    val posZMeters: Double = 0.0,
    val velXMps: Double = 0.0,
    val velYMps: Double = 0.0,
    val velZMps: Double = 0.0,
    val clockBiasMeters: Double = 0.0,
    val clockDriftMps: Double = 0.0,
    val ionoDelayMeters: Double = 0.0,
    val tropoDelayMeters: Double = 0.0
)

/**
 * Raw per-satellite GNSS measurement from GnssMeasurement.
 */
data class RawMeasurementData(
    val svid: Int,
    val constellation: ConstellationType,
    val rawConstellationType: Int,
    val timeOffsetNanos: Double,
    val state: Int,
    val receivedSvTimeNanos: Long,
    val receivedSvTimeUncertaintyNanos: Long,
    val cn0DbHz: Double,
    val hasBasebandCn0DbHz: Boolean = false,
    val basebandCn0DbHz: Double = 0.0,
    val pseudorangeRateMetersPerSecond: Double,
    val pseudorangeRateUncertaintyMetersPerSecond: Double,
    val accumulatedDeltaRangeState: Int,
    val accumulatedDeltaRangeMeters: Double,
    val accumulatedDeltaRangeUncertaintyMeters: Double,
    val hasCarrierFrequencyHz: Boolean = false,
    val carrierFrequencyHz: Float = 0f,
    val multipathIndicator: Int = 0,
    val hasSnrInDb: Boolean = false,
    val snrInDb: Double = 0.0,
    val hasAutomaticGainControlLevelDb: Boolean = false,
    val automaticGainControlLevelDb: Double = 0.0,
    val hasCodeType: Boolean = false,
    val codeType: String = "",
    val hasFullInterSignalBiasNanos: Boolean = false,
    val fullInterSignalBiasNanos: Double = 0.0,
    val satellitePvt: SatellitePvtData? = null,
    val receivedElapsedRealtimeNanos: Long = 0L
) {
    val frequencyBandLabel: String get() {
        if (!hasCarrierFrequencyHz) return "Unknown"
        return when {
            carrierFrequencyHz in 1_574_000_000f..1_577_000_000f -> when (constellation) {
                ConstellationType.GALILEO -> "E1"
                ConstellationType.BEIDOU -> "B1C"
                else -> "L1"
            }
            carrierFrequencyHz in 1_175_000_000f..1_178_000_000f -> when (constellation) {
                ConstellationType.GALILEO -> "E5a"
                ConstellationType.BEIDOU -> "B2a"
                else -> "L5"
            }
            carrierFrequencyHz in 1_206_000_000f..1_209_000_000f -> "E5b"
            carrierFrequencyHz in 1_226_000_000f..1_229_000_000f -> "L2"
            carrierFrequencyHz in 1_598_000_000f..1_606_000_000f -> "GLO L1"
            carrierFrequencyHz in 1_242_000_000f..1_250_000_000f -> "GLO L2"
            carrierFrequencyHz in 1_560_000_000f..1_562_000_000f -> "B1I"
            else -> "%.2f MHz".format(carrierFrequencyHz / 1_000_000f)
        }
    }
}

data class AgcMeasurementData(
    val constellation: ConstellationType,
    val carrierFrequencyHz: Long,
    val levelDb: Double,
    val timestampMs: Long
)

data class NavMessageData(
    val svid: Int,
    val type: Int,
    val typeLabel: String,
    val messageId: Int,
    val submessageId: Int,
    val status: Int,
    val dataHex: String,
    val dataBytes: ByteArray,
    val timestampMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NavMessageData) return false
        return svid == other.svid && type == other.type && messageId == other.messageId &&
            submessageId == other.submessageId && timestampMs == other.timestampMs
    }
    override fun hashCode(): Int = svid * 31 + type * 17 + timestampMs.hashCode()
}

data class NmeaMessageData(
    val sentence: String,
    val timestampMs: Long
)

data class NmeaGgaInfo(
    val mslAltitudeMeters: Double? = null,
    val geoidSeparationMeters: Double? = null,
    val fixQuality: Int = 0,
    val hdop: Float? = null,
    val timestampMs: Long = 0L
)

enum class CapabilityStatus(val label: String) {
    SUPPORTED("Supported"),
    UNSUPPORTED("Unsupported"),
    AVAILABLE_NOW("Observed Live"),
    NOT_OBSERVED("Not Observed"),
    UNKNOWN("Unknown (API Limit)")
}

data class CapabilityItem(
    val name: String,
    val category: String,
    val status: CapabilityStatus,
    val detail: String,
    val isObserved: Boolean = false
)

data class AntennaInfoSummary(
    val carrierFrequencyMHz: Double,
    val phaseCenterOffsetMm: String,
    val hasPhaseCenterVariations: Boolean,
    val hasSignalGainCorrections: Boolean
)

data class HardwareCapabilitiesReport(
    val hardwareModel: String = "Unknown",
    val hardwareYear: Int = 0,
    val androidApiLevel: Int = 0,
    val capabilities: List<CapabilityItem> = emptyList(),
    val antennaInfos: List<AntennaInfoSummary> = emptyList(),
    val observedConstellations: Set<ConstellationType> = emptySet(),
    val observedBands: Set<String> = emptySet(),
    val measurementRateHz: Float = 0f,
    val lastUpdatedMs: Long = 0L
)

data class ReceiverControlState(
    val fullTrackingEnabled: Boolean = false,
    val measurementIntervalMs: Int = 1000,
    val highPerformanceMode: Boolean = false,
    val ttffMillis: Long? = null,
    val isMeasuringTtff: Boolean = false,
    val measurementRateHz: Float = 0f,
    val statusMessage: String = "Default OS-managed GNSS mode"
)
