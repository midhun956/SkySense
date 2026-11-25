package com.skysense.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

data class AstronomyReport(
    val julianDate: Double,
    val modifiedJulianDate: Double,
    val gmstHours: Double,
    val lstHours: Double,
    val localSolarTimeHours: Double,
    val sunAzimuthDeg: Double,
    val sunElevationDeg: Double,
    val moonAzimuthDeg: Double,
    val moonElevationDeg: Double,
    val moonIlluminationPct: Double,
    val moonPhaseName: String,
    val sunriseLocal: String,
    val sunsetLocal: String,
    val solarNoonLocal: String,
    val daylightDurationText: String,
    val civilTwilight: String,
    val nauticalTwilight: String,
    val astronomicalTwilight: String
)

/**
 * Pure offline astronomical calculator (Jean Meeus / NOAA low-precision solar & lunar algorithms).
 */
object AstronomyEngine {

    private val hhmmFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun compute(
        unixSeconds: Double,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AstronomyReport {
        val jd = unixSeconds / 86400.0 + 2440587.5
        val mjd = jd - 2400000.5
        val d = jd - 2451545.0 // days since J2000.0
        val t = d / 36525.0

        // Greenwich Mean Sidereal Time (hours)
        val gmstDeg = norm360(280.46061837 + 360.98564736629 * d + 0.000387933 * t * t - t * t * t / 38710000.0)
        val gmstHours = gmstDeg / 15.0
        val lstHours = norm24(gmstHours + longitudeDeg / 15.0)

        // Sun position
        val sunMeanAnomaly = Math.toRadians(norm360(357.52911 + 0.98560028 * d))
        val sunMeanLon = norm360(280.46646 + 0.98564736 * d)
        val sunEclipticLon = Math.toRadians(
            norm360(sunMeanLon + 1.914602 * sin(sunMeanAnomaly) + 0.019993 * sin(2 * sunMeanAnomaly))
        )
        val obliquity = Math.toRadians(23.439291 - 0.0130042 * t)
        val sunRa = atan2(cos(obliquity) * sin(sunEclipticLon), cos(sunEclipticLon))
        val sunDec = asin(sin(obliquity) * sin(sunEclipticLon))

        val (sunAz, sunEl) = equatToHoriz(sunRa, sunDec, lstHours, latitudeDeg)

        // Equation of time (minutes) & Local Solar Time
        val sunRaHours = norm24(Math.toDegrees(sunRa) / 15.0)
        val eqTimeHours = norm12Signed(sunMeanLon / 15.0 - sunRaHours)
        val instant = Instant.ofEpochSecond(unixSeconds.toLong())
        val utcZoned = instant.atZone(ZoneOffset.UTC)
        val utcHours = utcZoned.hour + utcZoned.minute / 60.0 + utcZoned.second / 3600.0
        val localSolarTimeHours = norm24(utcHours + longitudeDeg / 15.0 + eqTimeHours)

        // Moon position & phase (low-precision Meeus)
        val moonL = norm360(218.3164477 + 13.17639648 * d)
        val moonM = Math.toRadians(norm360(134.9633964 + 13.06499295 * d))
        val moonF = Math.toRadians(norm360(93.2720950 + 13.22935024 * d))
        val moonLon = Math.toRadians(
            norm360(moonL + 6.289 * sin(moonM) + 1.274 * sin(2 * Math.toRadians(moonL) - sunEclipticLon - moonM))
        )
        val moonLat = Math.toRadians(5.128 * sin(moonF))
        val moonRa = atan2(
            sin(moonLon) * cos(obliquity) - tan(moonLat) * sin(obliquity),
            cos(moonLon)
        )
        val moonDec = asin(sin(moonLat) * cos(obliquity) + cos(moonLat) * sin(obliquity) * sin(moonLon))
        val (moonAz, moonEl) = equatToHoriz(moonRa, moonDec, lstHours, latitudeDeg)

        // Phase angle & illumination
        val phaseAngleDeg = norm360(Math.toDegrees(moonLon - sunEclipticLon))
        val illumPct = (1.0 - cos(Math.toRadians(phaseAngleDeg))) * 50.0
        val phaseName = moonPhaseLabel(phaseAngleDeg)

        // Rise / Set / Twilight for current UTC date
        val startOfDayUtc = utcZoned.toLocalDate().atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val noonUtcHours = 12.0 - longitudeDeg / 15.0 - eqTimeHours
        val solarNoonLocal = formatUtcHoursInZone(startOfDayUtc, noonUtcHours, zoneId)

        val (sr, ss, dur) = riseSetForDepression(startOfDayUtc, noonUtcHours, latitudeDeg, sunDec, -0.833, zoneId)
        val (civM, civE, _) = riseSetForDepression(startOfDayUtc, noonUtcHours, latitudeDeg, sunDec, -6.0, zoneId)
        val (nautM, nautE, _) = riseSetForDepression(startOfDayUtc, noonUtcHours, latitudeDeg, sunDec, -12.0, zoneId)
        val (astroM, astroE, _) = riseSetForDepression(startOfDayUtc, noonUtcHours, latitudeDeg, sunDec, -18.0, zoneId)

        return AstronomyReport(
            julianDate = jd,
            modifiedJulianDate = mjd,
            gmstHours = gmstHours,
            lstHours = lstHours,
            localSolarTimeHours = localSolarTimeHours,
            sunAzimuthDeg = sunAz,
            sunElevationDeg = sunEl,
            moonAzimuthDeg = moonAz,
            moonElevationDeg = moonEl,
            moonIlluminationPct = illumPct,
            moonPhaseName = phaseName,
            sunriseLocal = sr,
            sunsetLocal = ss,
            solarNoonLocal = solarNoonLocal,
            daylightDurationText = dur,
            civilTwilight = "$civM – $civE",
            nauticalTwilight = "$nautM – $nautE",
            astronomicalTwilight = "$astroM – $astroE"
        )
    }

    private fun equatToHoriz(raRad: Double, decRad: Double, lstHours: Double, latDeg: Double): Pair<Double, Double> {
        val lstRad = Math.toRadians(lstHours * 15.0)
        val ha = lstRad - raRad
        val latRad = Math.toRadians(latDeg)
        val sinAlt = sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(ha)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
        val cosAz = (sin(decRad) - sin(alt) * sin(latRad)) / (cos(alt) * cos(latRad)).coerceAtLeast(1e-9)
        var az = Math.toDegrees(acos(cosAz.coerceIn(-1.0, 1.0)))
        if (sin(ha) > 0) az = 360.0 - az
        return Pair(az, Math.toDegrees(alt))
    }

    private fun riseSetForDepression(
        startOfDayUtcSec: Long,
        noonUtcHours: Double,
        latDeg: Double,
        sunDecRad: Double,
        altDeg: Double,
        zoneId: ZoneId
    ): Triple<String, String, String> {
        val latRad = Math.toRadians(latDeg)
        val cosH = (sin(Math.toRadians(altDeg)) - sin(latRad) * sin(sunDecRad)) /
            (cos(latRad) * cos(sunDecRad)).coerceAtLeast(1e-9)
        if (cosH > 1.0) return Triple("Never", "Never", "0h 00m")
        if (cosH < -1.0) return Triple("Always", "Always", "24h 00m")
        val hHours = Math.toDegrees(acos(cosH)) / 15.0
        val rise = formatUtcHoursInZone(startOfDayUtcSec, noonUtcHours - hHours, zoneId)
        val set = formatUtcHoursInZone(startOfDayUtcSec, noonUtcHours + hHours, zoneId)
        val totalMin = (2.0 * hHours * 60.0).roundToInt()
        val dur = "${totalMin / 60}h %02dm".format(Locale.US, totalMin % 60)
        return Triple(rise, set, dur)
    }

    private fun formatUtcHoursInZone(startOfDayUtcSec: Long, utcHours: Double, zoneId: ZoneId): String {
        val sec = startOfDayUtcSec + (utcHours * 3600.0).roundToLong()
        return Instant.ofEpochSecond(sec).atZone(zoneId).format(hhmmFmt)
    }

    private fun moonPhaseLabel(angleDeg: Double): String = when {
        angleDeg < 22.5 || angleDeg >= 337.5 -> "New Moon"
        angleDeg < 67.5 -> "Waxing Crescent"
        angleDeg < 112.5 -> "First Quarter"
        angleDeg < 157.5 -> "Waxing Gibbous"
        angleDeg < 202.5 -> "Full Moon"
        angleDeg < 247.5 -> "Waning Gibbous"
        angleDeg < 292.5 -> "Last Quarter"
        else -> "Waning Crescent"
    }

    fun formatHoursHms(hours: Double): String {
        val h = norm24(hours)
        val hh = floor(h).toInt()
        val mm = floor((h - hh) * 60.0).toInt()
        val ss = ((h - hh - mm / 60.0) * 3600.0).toInt().coerceIn(0, 59)
        return "%02d:%02d:%02d".format(Locale.US, hh, mm, ss)
    }

    private fun norm360(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
    private fun norm24(h: Double): Double = ((h % 24.0) + 24.0) % 24.0
    private fun norm12Signed(h: Double): Double {
        var r = ((h + 12.0) % 24.0 + 24.0) % 24.0 - 12.0
        if (r < -12.0) r += 24.0
        return r
    }
}
