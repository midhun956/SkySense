package com.skysense.app.domain

import com.skysense.app.data.model.ConstellationType
import com.skysense.app.data.model.SatelliteInfo
import kotlin.math.*

data class TleEntry(
    val name: String,
    val constellation: ConstellationType,
    val line1: String,
    val line2: String,
    val epochJulianDate: Double,
    val inclinationRad: Double,
    val raanRad: Double,
    val eccentricity: Double,
    val argPerigeeRad: Double,
    val meanAnomalyRad: Double,
    val meanMotionRevPerDay: Double
)

data class PredictedSatellite(
    val name: String,
    val constellation: ConstellationType,
    val azimuthDeg: Double,
    val elevationDeg: Double
)

/**
 * Lightweight offline TLE Keplerian propagator for GNSS satellites (MEO/IGSO/GEO).
 */
object OrbitalPropagator {

    private const val MU_EARTH = 398600.4418 // km^3 / s^2
    private const val EARTH_RADIUS_KM = 6378.137
    private const val J2 = 1.08262668e-3
    private const val TWO_PI = 2.0 * PI

    fun parseTleCatalog(rawText: String): List<TleEntry> {
        val lines = rawText.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val result = mutableListOf<TleEntry>()
        var i = 0
        while (i + 2 < lines.size) {
            val l0 = lines[i]
            val l1 = lines[i + 1]
            val l2 = lines[i + 2]
            if (l1.startsWith("1 ") && l2.startsWith("2 ")) {
                parseSingleTle(l0.trim(), l1, l2)?.let { result.add(it) }
                i += 3
            } else {
                i += 1
            }
        }
        return result
    }

    private fun parseSingleTle(name: String, l1: String, l2: String): TleEntry? {
        return runCatching {
            val epochYear2 = l1.substring(18, 20).trim().toInt()
            val year = if (epochYear2 < 57) 2000 + epochYear2 else 1900 + epochYear2
            val dayOfYear = l1.substring(20, 32).trim().toDouble()
            val epochJd = julianDateFromYearDay(year, dayOfYear)

            val inc = Math.toRadians(l2.substring(8, 16).trim().toDouble())
            val raan = Math.toRadians(l2.substring(17, 25).trim().toDouble())
            val ecc = ("0." + l2.substring(26, 33).trim()).toDouble()
            val argP = Math.toRadians(l2.substring(34, 42).trim().toDouble())
            val ma = Math.toRadians(l2.substring(43, 51).trim().toDouble())
            val mm = l2.substring(52, 63).trim().toDouble()

            val constellation = when {
                name.contains("GPS", ignoreCase = true) || name.contains("NAVSTAR", ignoreCase = true) -> ConstellationType.GPS
                name.contains("GALILEO", ignoreCase = true) || name.contains("GSAT", ignoreCase = true) -> ConstellationType.GALILEO
                name.contains("COSMOS", ignoreCase = true) || name.contains("GLONASS", ignoreCase = true) -> ConstellationType.GLONASS
                name.contains("BEIDOU", ignoreCase = true) -> ConstellationType.BEIDOU
                name.contains("QZS", ignoreCase = true) || name.contains("MICHIBIKI", ignoreCase = true) -> ConstellationType.QZSS
                name.contains("IRNSS", ignoreCase = true) || name.contains("NVS", ignoreCase = true) -> ConstellationType.IRNSS
                else -> ConstellationType.UNKNOWN
            }

            TleEntry(name, constellation, l1, l2, epochJd, inc, raan, ecc, argP, ma, mm)
        }.getOrNull()
    }

    fun predictVisibleSatellites(
        catalog: List<TleEntry>,
        unixSeconds: Double,
        obsLatDeg: Double,
        obsLonDeg: Double,
        minElevationDeg: Double = 5.0
    ): List<PredictedSatellite> {
        val jd = unixSeconds / 86400.0 + 2440587.5
        val gmstRad = gmstRadians(jd)
        val latRad = Math.toRadians(obsLatDeg)
        val lonRad = Math.toRadians(obsLonDeg)

        val obsEcef = geodeticToEcefKm(latRad, lonRad, 0.0)
        val result = mutableListOf<PredictedSatellite>()

        for (tle in catalog) {
            val dtSec = (jd - tle.epochJulianDate) * 86400.0
            val nRadPerSec = tle.meanMotionRevPerDay * TWO_PI / 86400.0
            val a = (MU_EARTH / (nRadPerSec * nRadPerSec)).pow(1.0 / 3.0)
            val p = a * (1.0 - tle.eccentricity * tle.eccentricity)

            // J2 secular precession rates
            val factor = -1.5 * J2 * (EARTH_RADIUS_KM / p).pow(2.0) * nRadPerSec
            val raanDot = factor * cos(tle.inclinationRad)
            val argPDot = factor * (2.5 * sin(tle.inclinationRad).pow(2.0) - 2.0)

            val m = (tle.meanAnomalyRad + nRadPerSec * dtSec) % TWO_PI
            var eAnom = m
            repeat(5) {
                eAnom -= (eAnom - tle.eccentricity * sin(eAnom) - m) / (1.0 - tle.eccentricity * cos(eAnom))
            }

            val sinNu = sqrt(1.0 - tle.eccentricity * tle.eccentricity) * sin(eAnom) / (1.0 - tle.eccentricity * cos(eAnom))
            val cosNu = (cos(eAnom) - tle.eccentricity) / (1.0 - tle.eccentricity * cos(eAnom))
            val nu = atan2(sinNu, cosNu)
            val r = a * (1.0 - tle.eccentricity * cos(eAnom))

            val u = nu + tle.argPerigeeRad + argPDot * dtSec
            val raan = tle.raanRad + raanDot * dtSec
            val inc = tle.inclinationRad

            // ECI position (km)
            val xEci = r * (cos(raan) * cos(u) - sin(raan) * sin(u) * cos(inc))
            val yEci = r * (sin(raan) * cos(u) + cos(raan) * sin(u) * cos(inc))
            val zEci = r * (sin(u) * sin(inc))

            // ECI -> ECEF
            val xEcef = xEci * cos(gmstRad) + yEci * sin(gmstRad)
            val yEcef = -xEci * sin(gmstRad) + yEci * cos(gmstRad)
            val zEcef = zEci

            // Topocentric ENU
            val dx = xEcef - obsEcef[0]
            val dy = yEcef - obsEcef[1]
            val dz = zEcef - obsEcef[2]

            val east = -sin(lonRad) * dx + cos(lonRad) * dy
            val north = -sin(latRad) * cos(lonRad) * dx - sin(latRad) * sin(lonRad) * dy + cos(latRad) * dz
            val up = cos(latRad) * cos(lonRad) * dx + cos(latRad) * sin(lonRad) * dy + sin(latRad) * dz

            val horiz = sqrt(east * east + north * north)
            val elDeg = Math.toDegrees(atan2(up, horiz))
            if (elDeg >= minElevationDeg) {
                val azDeg = (Math.toDegrees(atan2(east, north)) + 360.0) % 360.0
                result.add(PredictedSatellite(tle.name, tle.constellation, azDeg, elDeg))
            }
        }
        return result
    }

    fun estimateFuturePdop(predicted: List<PredictedSatellite>): Float {
        val asSatInfo = predicted.mapIndexed { idx, p ->
            SatelliteInfo(
                svid = idx + 1,
                constellation = p.constellation,
                elevationDegrees = p.elevationDeg.toFloat(),
                azimuthDegrees = p.azimuthDeg.toFloat(),
                cn0DbHz = 35f,
                usedInFix = true
            )
        }
        val dop = GnssMath.computeTrueDop(asSatInfo)
        return if (dop.isValid) dop.pdop else 99.9f
    }

    private fun geodeticToEcefKm(latRad: Double, lonRad: Double, altKm: Double): DoubleArray {
        val e2 = 0.00669437999014
        val n = EARTH_RADIUS_KM / sqrt(1.0 - e2 * sin(latRad) * sin(latRad))
        return doubleArrayOf(
            (n + altKm) * cos(latRad) * cos(lonRad),
            (n + altKm) * cos(latRad) * sin(lonRad),
            (n * (1.0 - e2) + altKm) * sin(latRad)
        )
    }

    private fun gmstRadians(jd: Double): Double {
        val d = jd - 2451545.0
        val t = d / 36525.0
        val deg = (280.46061837 + 360.98564736629 * d + 0.000387933 * t * t) % 360.0
        return Math.toRadians((deg + 360.0) % 360.0)
    }

    private fun julianDateFromYearDay(year: Int, dayOfYear: Double): Double {
        val y = year - 1
        val a = y / 100
        val b = 2 - a + a / 4
        val jdJan0 = floor(365.25 * (y + 4716)) + floor(30.6001 * 14) + 0 + b - 1524.5
        return jdJan0 + dayOfYear
    }
}
