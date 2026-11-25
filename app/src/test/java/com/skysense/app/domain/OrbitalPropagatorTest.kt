package com.skysense.app.domain

import com.skysense.app.data.model.ConstellationType
import org.junit.Assert.*
import org.junit.Test

class OrbitalPropagatorTest {

    @Test
    fun `parses GPS TLE and propagates valid topocentric coordinates`() {
        val sampleTle = """
            GPS BIIR-2  (PRN 13)
            1 24876U 97035A   24001.50000000  .00000042  00000-0  00000-0 0  9991
            2 24876  55.4321 123.4567 0054321 234.5678 125.4321  2.00563210193456
        """.trimIndent()

        val catalog = OrbitalPropagator.parseTleCatalog(sampleTle)
        assertEquals(1, catalog.size)
        assertEquals(ConstellationType.GPS, catalog[0].constellation)

        val predicted = OrbitalPropagator.predictVisibleSatellites(
            catalog = catalog,
            unixSeconds = 1_704_110_400.0,
            obsLatDeg = 37.7749,
            obsLonDeg = -122.4194,
            minElevationDeg = -90.0
        )
        assertEquals(1, predicted.size)
        assertTrue(predicted[0].azimuthDeg in 0.0..360.0)
        assertTrue(predicted[0].elevationDeg in -90.0..90.0)
    }
}
