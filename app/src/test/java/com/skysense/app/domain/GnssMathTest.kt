package com.skysense.app.domain

import android.location.GnssMeasurement
import com.skysense.app.data.model.ConstellationType
import com.skysense.app.data.model.RawClockData
import com.skysense.app.data.model.RawMeasurementData
import com.skysense.app.data.model.SatelliteInfo
import org.junit.Assert.*
import org.junit.Test

class GnssMathTest {

    @Test
    fun `computeTrueDop returns valid DOP for well-distributed 4+ satellites`() {
        val sats = listOf(
            SatelliteInfo(1, ConstellationType.GPS, 15f, 0f, 38f, true),
            SatelliteInfo(2, ConstellationType.GPS, 15f, 120f, 36f, true),
            SatelliteInfo(3, ConstellationType.GPS, 15f, 240f, 39f, true),
            SatelliteInfo(4, ConstellationType.GPS, 85f, 45f, 42f, true)
        )
        val dop = GnssMath.computeTrueDop(sats)
        assertTrue(dop.isValid)
        assertTrue(dop.hdop in 0.8f..2.5f)
        assertTrue(dop.vdop in 0.8f..3.0f)
        assertTrue(dop.pdop >= dop.hdop)
        assertTrue(dop.gdop >= dop.pdop)
    }

    @Test
    fun `computeTrueDop returns invalid for fewer than 4 satellites`() {
        val sats = listOf(
            SatelliteInfo(1, ConstellationType.GPS, 30f, 0f, 38f, true),
            SatelliteInfo(2, ConstellationType.GPS, 45f, 90f, 36f, true),
            SatelliteInfo(3, ConstellationType.GPS, 60f, 180f, 39f, true)
        )
        val dop = GnssMath.computeTrueDop(sats)
        assertFalse(dop.isValid)
    }

    @Test
    fun `computePseudorangeMeters computes ~20200km to ~26000km GPS range`() {
        // Suppose GPS week 2300, TOW = 100_000 s
        val weekNanos = 2300L * GnssMath.NANOS_PER_WEEK
        val towNanos = 100_000L * 1_000_000_000L
        val tRxGpsNanos = weekNanos + towNanos
        val travelNanos = 70_000_000L // 70 ms -> ~20,985 km

        val clock = RawClockData(
            timeNanos = 500_000_000_000L,
            hasFullBiasNanos = true,
            fullBiasNanos = 500_000_000_000L - tRxGpsNanos,
            hasBiasNanos = true,
            biasNanos = 0.0,
            isValid = true
        )
        val meas = RawMeasurementData(
            svid = 5,
            constellation = ConstellationType.GPS,
            rawConstellationType = 1,
            timeOffsetNanos = 0.0,
            state = GnssMeasurement.STATE_TOW_DECODED,
            receivedSvTimeNanos = towNanos - travelNanos,
            receivedSvTimeUncertaintyNanos = 10L,
            cn0DbHz = 40.0,
            pseudorangeRateMetersPerSecond = 250.0,
            pseudorangeRateUncertaintyMetersPerSecond = 0.1,
            accumulatedDeltaRangeState = 0,
            accumulatedDeltaRangeMeters = 0.0,
            accumulatedDeltaRangeUncertaintyMeters = 0.0
        )

        val pr = GnssMath.computePseudorangeMeters(meas, clock)
        assertNotNull(pr)
        assertEquals(0.07 * GnssMath.SPEED_OF_LIGHT_MPS, pr!!, 1.0)
    }

    @Test
    fun `computeDopplerShiftHz has opposite sign to pseudorange rate`() {
        val doppler = GnssMath.computeDopplerShiftHz(500.0, 1_575_420_000f)
        assertNotNull(doppler)
        assertTrue(doppler!! < 0.0)
        assertEquals(-2627.5, doppler, 1.0)
    }

    @Test
    fun `computeCep calculates 50th and 95th percentile radii`() {
        val offsets = (1..100).map { i -> Pair(i.toDouble(), 0.0) }
        val (cep50, cep95) = GnssMath.computeCep(offsets)
        assertEquals(50.0, cep50, 1.0)
        assertEquals(95.0, cep95, 1.0)
    }
}
