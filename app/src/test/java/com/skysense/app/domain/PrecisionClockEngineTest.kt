package com.skysense.app.domain

import com.skysense.app.data.model.RawClockData
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneOffset

class PrecisionClockEngineTest {

    @Test
    fun `unavailable when clock has no full bias`() {
        val clock = RawClockData(isValid = true, hasFullBiasNanos = false)
        val state = PrecisionClockEngine.compute(clock, 1_000_000_000L, 1_700_000_000_000L)
        assertEquals(ClockSyncStatus.UNAVAILABLE, state.syncStatus)
    }

    @Test
    fun `computes GPS week, TOW, UTC and sync status transitions`() {
        val gpsWeek = 2300L
        val towSec = 12345.0
        val gpsTimeNanos = gpsWeek * GnssMath.NANOS_PER_WEEK + (towSec * GnssMath.NANOS_PER_SECOND).toLong()
        val timeNanos = 1_000_000_000_000L
        val fullBiasNanos = timeNanos - gpsTimeNanos
        val rxElapsed = 50_000_000_000L

        val clock = RawClockData(
            timeNanos = timeNanos,
            hasFullBiasNanos = true,
            fullBiasNanos = fullBiasNanos,
            hasBiasNanos = true,
            biasNanos = 0.0,
            hasLeapSecond = true,
            leapSecond = 18,
            hasDriftNanosPerSecond = true,
            driftNanosPerSecond = 0.0,
            receivedElapsedRealtimeNanos = rxElapsed,
            receivedSystemTimeMs = 1_700_000_000_000L,
            isValid = true
        )

        val synced = PrecisionClockEngine.compute(clock, rxElapsed + 50_000_000L, 1_700_000_000_000L, ZoneOffset.UTC)
        assertEquals(ClockSyncStatus.SYNCED, synced.syncStatus)
        assertEquals(gpsWeek, synced.gpsWeek)
        assertEquals(towSec + 0.05, synced.gpsTowSeconds, 1e-4)

        val stillSynced = PrecisionClockEngine.compute(clock, rxElapsed + 500_000_000L, 1_700_000_000_000L, ZoneOffset.UTC)
        assertEquals(ClockSyncStatus.SYNCED, stillSynced.syncStatus)

        val stale = PrecisionClockEngine.compute(clock, rxElapsed + 4_000_000_000L, 1_700_000_000_000L, ZoneOffset.UTC)
        assertEquals(ClockSyncStatus.STALE, stale.syncStatus)

        val lost = PrecisionClockEngine.compute(clock, rxElapsed + 15_000_000_000L, 1_700_000_000_000L, ZoneOffset.UTC)
        assertEquals(ClockSyncStatus.SIGNAL_LOST, lost.syncStatus)
    }
}
