package com.skysense.app.domain

import android.location.GnssCapabilities
import android.location.LocationManager
import android.os.Build
import com.skysense.app.data.model.*

/**
 * Inspects actual Android GNSS hardware capabilities via LocationManager & GnssCapabilities
 * combined with live observed telemetry. Never hardcodes chipset capabilities.
 */
object GnssHardwareInspector {

    fun inspect(
        locationManager: LocationManager,
        satellites: List<SatelliteInfo>,
        rawClock: RawClockData,
        rawMeasurements: List<RawMeasurementData>,
        agcMeasurements: List<AgcMeasurementData>,
        navMessages: List<NavMessageData>,
        measurementRateHz: Float
    ): HardwareCapabilitiesReport {
        val model = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.gnssHardwareModelName?.takeIf { it.isNotBlank() } ?: "Not Reported"
        } else {
            "Unknown (API < 28)"
        }
        val year = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.gnssYearOfHardware
        } else 0

        val caps: GnssCapabilities? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { locationManager.gnssCapabilities }.getOrNull()
        } else null

        val observedConstellations = satellites.map { it.constellation }
            .filter { it != ConstellationType.UNKNOWN }
            .toSet()

        val observedBands = buildSet {
            satellites.filter { it.hasCarrierFrequency }.forEach { add(it.frequencyBandLabel) }
            rawMeasurements.filter { it.hasCarrierFrequencyHz }.forEach { add(it.frequencyBandLabel) }
        }

        val hasMeasurements = rawMeasurements.isNotEmpty()
        val hasAdrObserved = rawMeasurements.any {
            (it.accumulatedDeltaRangeState and android.location.GnssMeasurement.ADR_STATE_VALID) != 0
        }
        val hasAgcObserved = agcMeasurements.isNotEmpty() || rawMeasurements.any { it.hasAutomaticGainControlLevelDb }
        val hasBasebandCn0Observed = rawMeasurements.any { it.hasBasebandCn0DbHz }
        val hasSatPvtObserved = rawMeasurements.any { it.satellitePvt != null }
        val hasCodeTypeObserved = rawMeasurements.any { it.hasCodeType && it.codeType.isNotBlank() }
        val hasMultiFreq = observedBands.size >= 2

        val antennaInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                locationManager.gnssAntennaInfos?.map { info ->
                    val pco = info.phaseCenterOffset
                    AntennaInfoSummary(
                        carrierFrequencyMHz = info.carrierFrequencyMHz,
                        phaseCenterOffsetMm = "X=%.2f, Y=%.2f, Z=%.2f mm".format(pco.xOffsetMm, pco.yOffsetMm, pco.zOffsetMm),
                        hasPhaseCenterVariations = info.phaseCenterVariationCorrections != null,
                        hasSignalGainCorrections = info.signalGainCorrections != null
                    )
                } ?: emptyList()
            }.getOrDefault(emptyList())
        } else emptyList()

        val items = mutableListOf<CapabilityItem>()

        // 1. Raw Measurements
        val rawStatus = when {
            hasMeasurements -> CapabilityStatus.AVAILABLE_NOW
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && caps != null -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (caps.hasMeasurements()) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED
                } else {
                    @Suppress("DEPRECATION")
                    if (caps.hasMeasurements()) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED
                }
            }
            else -> CapabilityStatus.NOT_OBSERVED
        }
        items.add(CapabilityItem(
            name = "Raw GNSS Measurements",
            category = "Core",
            status = rawStatus,
            detail = if (hasMeasurements) "${rawMeasurements.size} raw channels active" else "GnssMeasurementsEvent callback",
            isObserved = hasMeasurements
        ))

        // 2. Multi-frequency reception
        val freqStatus = when {
            hasMultiFreq -> CapabilityStatus.AVAILABLE_NOW
            observedBands.size == 1 -> CapabilityStatus.NOT_OBSERVED
            else -> CapabilityStatus.NOT_OBSERVED
        }
        items.add(CapabilityItem(
            name = "Multi-Frequency Reception",
            category = "RF & Bands",
            status = freqStatus,
            detail = if (observedBands.isEmpty()) "No carrier frequency observed yet" else "Observed: ${observedBands.sorted().joinToString(", ")}",
            isObserved = hasMultiFreq
        ))

        // 3. Carrier Phase / ADR
        val adrStatus = when {
            hasAdrObserved -> CapabilityStatus.AVAILABLE_NOW
            hasMeasurements -> CapabilityStatus.NOT_OBSERVED
            else -> CapabilityStatus.UNKNOWN
        }
        items.add(CapabilityItem(
            name = "Carrier-Phase (ADR)",
            category = "Precision",
            status = adrStatus,
            detail = if (hasAdrObserved) "Valid Accumulated Delta Range observed" else "Requires valid ADR_STATE_VALID lock",
            isObserved = hasAdrObserved
        ))

        // 4. AGC
        val agcStatus = when {
            hasAgcObserved -> CapabilityStatus.AVAILABLE_NOW
            Build.VERSION.SDK_INT >= 33 && caps != null -> {
                val supported = runCatching {
                    caps.javaClass.getMethod("hasGnssAutomaticGainControl").invoke(caps) as? Boolean
                }.getOrNull()
                when (supported) {
                    true -> CapabilityStatus.SUPPORTED
                    false -> CapabilityStatus.UNSUPPORTED
                    null -> CapabilityStatus.UNKNOWN
                }
            }
            else -> CapabilityStatus.NOT_OBSERVED
        }
        items.add(CapabilityItem(
            name = "Automatic Gain Control (AGC)",
            category = "RF & Bands",
            status = agcStatus,
            detail = if (hasAgcObserved) "Live AGC dB telemetry active" else "RF front-end gain monitor",
            isObserved = hasAgcObserved
        ))

        // 5. Navigation Messages
        val navMsgStatus = when {
            navMessages.isNotEmpty() -> CapabilityStatus.AVAILABLE_NOW
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && caps != null -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (caps.hasNavigationMessages()) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED
                } else CapabilityStatus.UNKNOWN
            }
            else -> CapabilityStatus.NOT_OBSERVED
        }
        items.add(CapabilityItem(
            name = "Navigation Messages",
            category = "Core",
            status = navMsgStatus,
            detail = if (navMessages.isNotEmpty()) "${navMessages.size} subframes/pages buffered" else "Raw broadcast ephemeris/almanac bits",
            isObserved = navMessages.isNotEmpty()
        ))

        // 6. Antenna Info
        val antStatus = when {
            antennaInfos.isNotEmpty() -> CapabilityStatus.AVAILABLE_NOW
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && caps != null -> {
                if (caps.hasAntennaInfo()) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED
            }
            else -> CapabilityStatus.UNKNOWN
        }
        items.add(CapabilityItem(
            name = "Antenna Calibration Info",
            category = "Hardware",
            status = antStatus,
            detail = if (antennaInfos.isNotEmpty()) "${antennaInfos.size} calibrated frequency profile(s)" else "Phase center offset & variation",
            isObserved = antennaInfos.isNotEmpty()
        ))

        // 7. Satellite PVT (API 34/35+)
        val pvtStatus = when {
            hasSatPvtObserved -> CapabilityStatus.AVAILABLE_NOW
            Build.VERSION.SDK_INT >= 34 && caps != null -> {
                runCatching {
                    val method = caps.javaClass.getMethod("hasSatellitePvt")
                    if (method.invoke(caps) as Boolean) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED
                }.getOrDefault(CapabilityStatus.UNKNOWN)
            }
            else -> CapabilityStatus.UNKNOWN
        }
        items.add(CapabilityItem(
            name = "Satellite PVT",
            category = "Precision",
            status = pvtStatus,
            detail = if (hasSatPvtObserved) "Hardware provides ECEF pos/vel & clock" else "Hardware-computed SV position/velocity",
            isObserved = hasSatPvtObserved
        ))

        // 8. Correlation Vectors
        val corrStatus = if (Build.VERSION.SDK_INT >= 34 && caps != null) {
            runCatching {
                val method = caps.javaClass.getMethod("hasMeasurementCorrelationVectors")
                if (method.invoke(caps) as Boolean) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED
            }.getOrDefault(CapabilityStatus.UNKNOWN)
        } else CapabilityStatus.UNKNOWN
        items.add(CapabilityItem(
            name = "Correlation Vectors",
            category = "RF & Bands",
            status = corrStatus,
            detail = "Multi-correlator tracking taps (API 34+)"
        ))

        // 9. Baseband C/N0
        val bbStatus = when {
            hasBasebandCn0Observed -> CapabilityStatus.AVAILABLE_NOW
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> CapabilityStatus.NOT_OBSERVED
            else -> CapabilityStatus.UNKNOWN
        }
        items.add(CapabilityItem(
            name = "Baseband C/N₀",
            category = "RF & Bands",
            status = bbStatus,
            detail = if (hasBasebandCn0Observed) "Baseband-level C/N₀ reported" else "Post-correlation baseband C/N₀ (API 30+)",
            isObserved = hasBasebandCn0Observed
        ))

        // 10. Signal Code Type
        items.add(CapabilityItem(
            name = "Signal Code Type",
            category = "RF & Bands",
            status = if (hasCodeTypeObserved) CapabilityStatus.AVAILABLE_NOW else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) CapabilityStatus.NOT_OBSERVED else CapabilityStatus.UNKNOWN,
            detail = if (hasCodeTypeObserved) rawMeasurements.mapNotNull { it.codeType.takeIf { c -> c.isNotBlank() } }.distinct().joinToString(", ") else "e.g. C/A, L5Q, E1C (API 29+)",
            isObserved = hasCodeTypeObserved
        ))

        // 11. Full Tracking Mode (API 31+)
        items.add(CapabilityItem(
            name = "Full Tracking Mode",
            category = "Receiver Control",
            status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED,
            detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "GnssMeasurementRequest.setFullTracking supported" else "Requires Android 12+ (API 31)"
        ))

        // 12. Hardware Clock Capabilities
        val clockCapsDetail = buildList {
            if (rawClock.hasFullBiasNanos) add("FullBias")
            if (rawClock.hasBiasUncertaintyNanos) add("BiasUnc")
            if (rawClock.hasDriftNanosPerSecond) add("Drift")
            if (rawClock.hasLeapSecond) add("LeapSec")
            if (rawClock.hasElapsedRealtimeNanos) add("ElapsedRealtime")
        }.joinToString(", ").ifEmpty { "Awaiting GnssClock epoch" }
        items.add(CapabilityItem(
            name = "Hardware Clock Telemetry",
            category = "Timing",
            status = if (rawClock.isValid) CapabilityStatus.AVAILABLE_NOW else CapabilityStatus.NOT_OBSERVED,
            detail = clockCapsDetail,
            isObserved = rawClock.isValid
        ))

        // 13. MSL Altitude (API 34+)
        items.add(CapabilityItem(
            name = "OS Geoid / MSL Altitude",
            category = "Altimetry",
            status = if (Build.VERSION.SDK_INT >= 34) CapabilityStatus.SUPPORTED else CapabilityStatus.UNKNOWN,
            detail = if (Build.VERSION.SDK_INT >= 34) "Android 14+ MslAltitude API + NMEA GGA" else "Uses NMEA GGA geoid separation fallback"
        ))

        return HardwareCapabilitiesReport(
            hardwareModel = model,
            hardwareYear = year,
            androidApiLevel = Build.VERSION.SDK_INT,
            capabilities = items,
            antennaInfos = antennaInfos,
            observedConstellations = observedConstellations,
            observedBands = observedBands,
            measurementRateHz = measurementRateHz,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }
}
