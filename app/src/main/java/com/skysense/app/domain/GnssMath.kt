package com.skysense.app.domain

import android.location.GnssMeasurement
import com.skysense.app.data.model.ConstellationType
import com.skysense.app.data.model.RawClockData
import com.skysense.app.data.model.RawMeasurementData
import com.skysense.app.data.model.SatelliteInfo
import kotlin.math.*

/**
 * Pure Kotlin GNSS scientific & geometric math utilities.
 */
object GnssMath {

    const val SPEED_OF_LIGHT_MPS = 299_792_458.0
    const val NANOS_PER_SECOND = 1_000_000_000.0
    const val SECONDS_PER_WEEK = 604_800.0
    const val NANOS_PER_WEEK = 604_800_000_000_000L
    const val NANOS_PER_DAY = 86_400_000_000_000L
    const val BEIDOU_GPS_EPOCH_OFFSET_SECONDS = 14L
    const val GLONASS_UTC_OFFSET_SECONDS = 10_800L // UTC+3 hours
    const val GPS_UNIX_EPOCH_OFFSET_SECONDS = 315_964_800L // 1980-01-06 minus 1970-01-01
    const val EARTH_RADIUS_METERS = 6_371_000.0

    data class DopResult(
        val gdop: Float,
        val pdop: Float,
        val hdop: Float,
        val vdop: Float,
        val tdop: Float,
        val isValid: Boolean = true
    )

    /**
     * Computes true GDOP, PDOP, HDOP, VDOP, TDOP by constructing the 4-column
     * geometry design matrix H in local East-North-Up (ENU) coordinates and
     * inverting (H^T * H).
     *
     * Requires at least 4 satellites with distinct geometry.
     */
    fun computeTrueDop(satellites: List<SatelliteInfo>): DopResult {
        val validSats = satellites.filter { it.elevationDegrees > 0f && it.elevationDegrees <= 90f }
        if (validSats.size < 4) {
            return DopResult(0f, 0f, 0f, 0f, 0f, isValid = false)
        }

        // Build H^T * H (4x4 symmetric matrix)
        val hth = Array(4) { DoubleArray(4) }
        for (sat in validSats) {
            val elRad = Math.toRadians(sat.elevationDegrees.toDouble())
            val azRad = Math.toRadians(sat.azimuthDegrees.toDouble())
            val cosEl = cos(elRad)
            val e = cosEl * sin(azRad)
            val n = cosEl * cos(azRad)
            val u = sin(elRad)
            val row = doubleArrayOf(e, n, u, 1.0)
            for (r in 0 until 4) {
                for (c in 0 until 4) {
                    hth[r][c] += row[r] * row[c]
                }
            }
        }

        val inv = invert4x4(hth) ?: return DopResult(0f, 0f, 0f, 0f, 0f, isValid = false)
        val qee = inv[0][0]
        val qnn = inv[1][1]
        val quu = inv[2][2]
        val qtt = inv[3][3]

        if (qee < 0 || qnn < 0 || quu < 0 || qtt < 0) {
            return DopResult(0f, 0f, 0f, 0f, 0f, isValid = false)
        }

        val hdop = sqrt(qee + qnn).toFloat().coerceAtMost(99.9f)
        val vdop = sqrt(quu).toFloat().coerceAtMost(99.9f)
        val pdop = sqrt(qee + qnn + quu).toFloat().coerceAtMost(99.9f)
        val tdop = sqrt(qtt).toFloat().coerceAtMost(99.9f)
        val gdop = sqrt(qee + qnn + quu + qtt).toFloat().coerceAtMost(99.9f)

        return DopResult(gdop = gdop, pdop = pdop, hdop = hdop, vdop = vdop, tdop = tdop, isValid = true)
    }

    /**
     * Standard 4x4 matrix inversion via Gauss-Jordan elimination with partial pivoting.
     */
    fun invert4x4(m: Array<DoubleArray>): Array<DoubleArray>? {
        val a = Array(4) { r -> DoubleArray(8) { c -> if (c < 4) m[r][c] else if (c - 4 == r) 1.0 else 0.0 } }
        for (col in 0 until 4) {
            var pivotRow = col
            var maxVal = abs(a[col][col])
            for (row in col + 1 until 4) {
                val v = abs(a[row][col])
                if (v > maxVal) {
                    maxVal = v
                    pivotRow = row
                }
            }
            if (maxVal < 1e-10) return null // Singular matrix (coplanar/collinear geometry)
            if (pivotRow != col) {
                val tmp = a[col]
                a[col] = a[pivotRow]
                a[pivotRow] = tmp
            }
            val pivot = a[col][col]
            for (c in 0 until 8) a[col][c] /= pivot
            for (row in 0 until 4) {
                if (row != col) {
                    val factor = a[row][col]
                    for (c in 0 until 8) {
                        a[row][c] -= factor * a[col][c]
                    }
                }
            }
        }
        return Array(4) { r -> DoubleArray(4) { c -> a[r][c + 4] } }
    }

    /**
     * Computes pseudorange (meters) from GnssClock & GnssMeasurement, respecting
     * constellation-specific time systems and TOW/TOD rollovers.
     * Returns null if clock bias or SV time state is insufficient.
     */
    fun computePseudorangeMeters(m: RawMeasurementData, clock: RawClockData): Double? {
        if (!clock.isValid || !clock.hasFullBiasNanos) return null
        val towDecoded = (m.state and GnssMeasurement.STATE_TOW_DECODED) != 0 ||
            (m.state and GnssMeasurement.STATE_TOW_KNOWN) != 0
        val gloTodKnown = (m.state and GnssMeasurement.STATE_GLO_TOD_DECODED) != 0 ||
            (m.state and GnssMeasurement.STATE_GLO_TOD_KNOWN) != 0

        val isGlonass = m.constellation == ConstellationType.GLONASS
        if (isGlonass && !gloTodKnown) return null
        if (!isGlonass && !towDecoded) return null

        // Receiver GPS time in nanoseconds
        val tRxGpsNanos = clock.timeNanos.toDouble() - (clock.fullBiasNanos.toDouble() + clock.biasNanos) + m.timeOffsetNanos
        val tTxNanos = m.receivedSvTimeNanos.toDouble()

        val dtNanos: Double = when (m.constellation) {
            ConstellationType.GLONASS -> {
                // GLONASS SV time is Time-of-Day in UTC(SU) = UTC + 3h
                val leapSec = if (clock.hasLeapSecond) clock.leapSecond.toLong() else 18L
                val tRxUtcNanos = tRxGpsNanos - leapSec * NANOS_PER_SECOND
                val tRxGloTodNanos = ((tRxUtcNanos + GLONASS_UTC_OFFSET_SECONDS * NANOS_PER_SECOND) % NANOS_PER_DAY + NANOS_PER_DAY) % NANOS_PER_DAY
                var diff = tRxGloTodNanos - tTxNanos
                if (diff > NANOS_PER_DAY / 2.0) diff -= NANOS_PER_DAY
                if (diff < -NANOS_PER_DAY / 2.0) diff += NANOS_PER_DAY
                diff
            }
            ConstellationType.BEIDOU -> {
                // BeiDou Time (BDT) is 14 seconds behind GPS Time
                val tRxBdtNanos = tRxGpsNanos - BEIDOU_GPS_EPOCH_OFFSET_SECONDS * NANOS_PER_SECOND
                val tRxTowNanos = ((tRxBdtNanos % NANOS_PER_WEEK) + NANOS_PER_WEEK) % NANOS_PER_WEEK
                var diff = tRxTowNanos - tTxNanos
                if (diff > NANOS_PER_WEEK / 2.0) diff -= NANOS_PER_WEEK
                if (diff < -NANOS_PER_WEEK / 2.0) diff += NANOS_PER_WEEK
                diff
            }
            else -> {
                // GPS, Galileo, QZSS, IRNSS all share GPS week/TOW modulo 1 week
                val tRxTowNanos = ((tRxGpsNanos % NANOS_PER_WEEK) + NANOS_PER_WEEK) % NANOS_PER_WEEK
                var diff = tRxTowNanos - tTxNanos
                if (diff > NANOS_PER_WEEK / 2.0) diff -= NANOS_PER_WEEK
                if (diff < -NANOS_PER_WEEK / 2.0) diff += NANOS_PER_WEEK
                diff
            }
        }

        val pr = (dtNanos / NANOS_PER_SECOND) * SPEED_OF_LIGHT_MPS
        // Sanity filter for physical Earth-to-MEO/GEO/IGSO GNSS ranges (15,000 km .. 45,000 km)
        return if (pr in 15_000_000.0..45_000_000.0) pr else null
    }

    /**
     * Doppler shift in Hz: Δf = -f_0 * (pseudorangeRate / c).
     * Positive pseudorange rate (satellite moving away) yields negative Doppler shift.
     */
    fun computeDopplerShiftHz(pseudorangeRateMps: Double, carrierFreqHz: Float): Double? {
        if (carrierFreqHz <= 0f) return null
        return -carrierFreqHz.toDouble() * (pseudorangeRateMps / SPEED_OF_LIGHT_MPS)
    }

    /**
     * Signal propagation time in milliseconds from pseudorange in meters.
     */
    fun computeSignalPropagationMs(pseudorangeMeters: Double): Double =
        (pseudorangeMeters / SPEED_OF_LIGHT_MPS) * 1000.0

    fun decodeMeasurementStateFlags(state: Int): List<String> {
        if (state == 0) return listOf("UNKNOWN")
        val flags = mutableListOf<String>()
        if (state and GnssMeasurement.STATE_CODE_LOCK != 0) flags.add("CODE_LOCK")
        if (state and GnssMeasurement.STATE_BIT_SYNC != 0) flags.add("BIT_SYNC")
        if (state and GnssMeasurement.STATE_SUBFRAME_SYNC != 0) flags.add("SUBFRAME_SYNC")
        if (state and GnssMeasurement.STATE_TOW_DECODED != 0) flags.add("TOW_DECODED")
        if (state and GnssMeasurement.STATE_MSEC_AMBIGUOUS != 0) flags.add("MSEC_AMBIGUOUS")
        if (state and GnssMeasurement.STATE_SYMBOL_SYNC != 0) flags.add("SYMBOL_SYNC")
        if (state and GnssMeasurement.STATE_GLO_STRING_SYNC != 0) flags.add("GLO_STRING_SYNC")
        if (state and GnssMeasurement.STATE_GLO_TOD_DECODED != 0) flags.add("GLO_TOD_DECODED")
        if (state and GnssMeasurement.STATE_BDS_D2_BIT_SYNC != 0) flags.add("BDS_D2_BIT_SYNC")
        if (state and GnssMeasurement.STATE_BDS_D2_SUBFRAME_SYNC != 0) flags.add("BDS_D2_SUBFRAME_SYNC")
        if (state and GnssMeasurement.STATE_GAL_E1BC_CODE_LOCK != 0) flags.add("GAL_E1BC_CODE_LOCK")
        if (state and GnssMeasurement.STATE_GAL_E1C_2ND_CODE_LOCK != 0) flags.add("GAL_E1C_2ND_CODE")
        if (state and GnssMeasurement.STATE_GAL_E1B_PAGE_SYNC != 0) flags.add("GAL_E1B_PAGE_SYNC")
        if (state and GnssMeasurement.STATE_SBAS_SYNC != 0) flags.add("SBAS_SYNC")
        if (state and GnssMeasurement.STATE_TOW_KNOWN != 0) flags.add("TOW_KNOWN")
        if (state and GnssMeasurement.STATE_GLO_TOD_KNOWN != 0) flags.add("GLO_TOD_KNOWN")
        if (state and GnssMeasurement.STATE_2ND_CODE_LOCK != 0) flags.add("2ND_CODE_LOCK")
        return flags
    }

    fun decodeAdrStateFlags(adrState: Int): List<String> {
        if (adrState == 0) return listOf("UNKNOWN")
        val flags = mutableListOf<String>()
        if (adrState and GnssMeasurement.ADR_STATE_VALID != 0) flags.add("VALID")
        if (adrState and GnssMeasurement.ADR_STATE_RESET != 0) flags.add("RESET")
        if (adrState and GnssMeasurement.ADR_STATE_CYCLE_SLIP != 0) flags.add("CYCLE_SLIP")
        if (adrState and GnssMeasurement.ADR_STATE_HALF_CYCLE_RESOLVED != 0) flags.add("HALF_CYCLE_RESOLVED")
        if (adrState and GnssMeasurement.ADR_STATE_HALF_CYCLE_REPORTED != 0) flags.add("HALF_CYCLE_REPORTED")
        return flags
    }

    /**
     * Converts (lat, lon) relative to (refLat, refLon) into local East & North offsets in meters.
     */
    fun enuOffsetMeters(refLatDeg: Double, refLonDeg: Double, latDeg: Double, lonDeg: Double): Pair<Double, Double> {
        val dLatRad = Math.toRadians(latDeg - refLatDeg)
        val dLonRad = Math.toRadians(lonDeg - refLonDeg)
        val cosLat = cos(Math.toRadians(refLatDeg))
        val east = dLonRad * EARTH_RADIUS_METERS * cosLat
        val north = dLatRad * EARTH_RADIUS_METERS
        return Pair(east, north)
    }

    /**
     * Computes CEP50 (50th percentile radius) and CEP95 (95th percentile radius)
     * from a list of (eastMeters, northMeters) offsets relative to the sample mean.
     */
    fun computeCep(offsets: List<Pair<Double, Double>>): Pair<Double, Double> {
        if (offsets.isEmpty()) return Pair(0.0, 0.0)
        val radii = offsets.map { (e, n) -> sqrt(e * e + n * n) }.sorted()
        val idx50 = ((radii.size - 1) * 0.50).toInt().coerceIn(radii.indices)
        val idx95 = ((radii.size - 1) * 0.95).toInt().coerceIn(radii.indices)
        return Pair(radii[idx50], radii[idx95])
    }
}
