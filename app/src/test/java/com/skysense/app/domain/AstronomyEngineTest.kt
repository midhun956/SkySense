package com.skysense.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneOffset

class AstronomyEngineTest {

    @Test
    fun `J2000 epoch yields Julian Date 2451545_0`() {
        // 2000-01-01T12:00:00Z = 946728000 unix seconds
        val report = AstronomyEngine.compute(946_728_000.0, 0.0, 0.0, ZoneOffset.UTC)
        assertEquals(2451545.0, report.julianDate, 1e-6)
        assertEquals(51544.5, report.modifiedJulianDate, 1e-6)
        assertTrue(report.gmstHours in 0.0..24.0)
        assertTrue(report.moonIlluminationPct in 0.0..100.0)
        assertTrue(report.sunAzimuthDeg in 0.0..360.0)
        assertTrue(report.sunElevationDeg in -90.0..90.0)
    }
}
