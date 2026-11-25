package com.skysense.app.domain

import com.skysense.app.data.model.RawClockData
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

enum class ClockSyncStatus(val label: String) {
    SYNCED("GNSS Locked"),
    INTERPOLATING("Monotonic Interp."),
    STALE("Stale Measurement"),
    SIGNAL_LOST("Signal Lost"),
    UNAVAILABLE("No Clock Bias")
}

data class PrecisionClockState(
    val syncStatus: ClockSyncStatus = ClockSyncStatus.UNAVAILABLE,
    val utcTimeText: String = "--:--:--",
    val utcDateText: String = "----/--/--",
    val localTimeText: String = "--:--:--",
    val gpsWeek: Long = 0L,
    val gpsTowSeconds: Double = 0.0,
    val gpsTotalSeconds: Double = 0.0,
    val unixTimestampSeconds: Double = 0.0,
    val leapSecond: Int = 18,
    val isLeapSecondKnown: Boolean = false,
    val fullBiasNanos: Long = 0L,
    val biasNanos: Double = 0.0,
    val biasUncertaintyNanos: Double = 0.0,
    val driftNanosPerSecond: Double = 0.0,
    val driftUncertaintyNanosPerSecond: Double = 0.0,
    val timeUncertaintyNanos: Double = 0.0,
    val gnssMinusSystemMs: Double = 0.0,
    val measurementAgeMs: Double = 0.0,
    val discontinuityCount: Int = 0
)

/**
 * Pure Kotlin satellite-derived clock engine using GnssClock + monotonic elapsedRealtimeNanos.
 */
object PrecisionClockEngine {

    private val timeFormatter24 = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val timeFormatter12 = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.US)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    fun compute(
        clock: RawClockData,
        currentElapsedNanos: Long,
        currentSystemTimeMs: Long,
        localZone: ZoneId = ZoneId.systemDefault(),
        use24HourFormat: Boolean = true
    ): PrecisionClockState {
        if (!clock.isValid || !clock.hasFullBiasNanos) {
            return PrecisionClockState(
                syncStatus = ClockSyncStatus.UNAVAILABLE,
                discontinuityCount = clock.hardwareClockDiscontinuityCount
            )
        }

        val dtElapsedNanos = (currentElapsedNanos - clock.receivedElapsedRealtimeNanos).coerceAtLeast(0L)
        val measurementAgeMs = dtElapsedNanos / 1_000_000.0

        val syncStatus = when {
            measurementAgeMs > 10_000.0 -> ClockSyncStatus.SIGNAL_LOST
            measurementAgeMs > 2_500.0 -> ClockSyncStatus.STALE
            else -> ClockSyncStatus.SYNCED
        }

        // Drift compensation during monotonic interpolation
        val dtSec = dtElapsedNanos / GnssMath.NANOS_PER_SECOND
        val driftCorrectionNanos = if (clock.hasDriftNanosPerSecond) {
            clock.driftNanosPerSecond * dtSec
        } else 0.0

        val baseGpsTimeNanos = clock.timeNanos.toDouble() - (clock.fullBiasNanos.toDouble() + clock.biasNanos)
        val interpolatedGpsTimeNanos = baseGpsTimeNanos + dtElapsedNanos.toDouble() - driftCorrectionNanos

        val gpsTotalSeconds = interpolatedGpsTimeNanos / GnssMath.NANOS_PER_SECOND
        val gpsWeek = floor(gpsTotalSeconds / GnssMath.SECONDS_PER_WEEK).toLong()
        val gpsTowSeconds = gpsTotalSeconds - gpsWeek * GnssMath.SECONDS_PER_WEEK

        val leapSec = if (clock.hasLeapSecond) clock.leapSecond else 18
        val unixSeconds = gpsTotalSeconds + GnssMath.GPS_UNIX_EPOCH_OFFSET_SECONDS - leapSec

        val wholeSec = floor(unixSeconds + 0.5).toLong()
        val instant = Instant.ofEpochSecond(wholeSec)
        val utcZoned = instant.atZone(ZoneOffset.UTC)
        val localZoned = instant.atZone(localZone)

        val fmt = if (use24HourFormat) timeFormatter24 else timeFormatter12
        val utcTimeText = utcZoned.format(fmt)
        val utcDateText = utcZoned.format(dateFormatter)
        val localTimeText = localZoned.format(fmt)

        val gnssUnixMs = unixSeconds * 1000.0
        val gnssMinusSystemMs = gnssUnixMs - currentSystemTimeMs.toDouble()

        val effectiveUncertaintyNanos = if (clock.hasBiasUncertaintyNanos) {
            val driftGrow = if (clock.hasDriftUncertaintyNanosPerSecond) clock.driftUncertaintyNanosPerSecond * dtSec else 0.0
            clock.biasUncertaintyNanos + driftGrow
        } else {
            clock.timeUncertaintyNanos
        }

        return PrecisionClockState(
            syncStatus = syncStatus,
            utcTimeText = utcTimeText,
            utcDateText = utcDateText,
            localTimeText = localTimeText,
            gpsWeek = gpsWeek,
            gpsTowSeconds = gpsTowSeconds,
            gpsTotalSeconds = gpsTotalSeconds,
            unixTimestampSeconds = unixSeconds,
            leapSecond = leapSec,
            isLeapSecondKnown = clock.hasLeapSecond,
            fullBiasNanos = clock.fullBiasNanos,
            biasNanos = clock.biasNanos,
            biasUncertaintyNanos = effectiveUncertaintyNanos,
            driftNanosPerSecond = clock.driftNanosPerSecond,
            driftUncertaintyNanosPerSecond = clock.driftUncertaintyNanosPerSecond,
            timeUncertaintyNanos = clock.timeUncertaintyNanos,
            gnssMinusSystemMs = gnssMinusSystemMs,
            measurementAgeMs = measurementAgeMs,
            discontinuityCount = clock.hardwareClockDiscontinuityCount
        )
    }
}
