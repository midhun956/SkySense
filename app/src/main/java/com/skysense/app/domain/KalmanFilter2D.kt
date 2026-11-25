package com.skysense.app.domain

import kotlin.math.cos

/**
 * Simple 2D constant-velocity Kalman filter operating on local East-North meters
 * around an anchor (lat0, lon0), used for experimental fix smoothing in Positioning Lab.
 */
class KalmanFilter2D(
    private val processNoisePos: Double = 0.5,
    private val processNoiseVel: Double = 0.2
) {
    private var initialized = false
    private var refLat = 0.0
    private var refLon = 0.0

    // State: [east, north, vEast, vNorth]
    private var e = 0.0
    private var n = 0.0
    private var ve = 0.0
    private var vn = 0.0

    // Diagonal covariance approximation for fast, stable updates
    private var pe = 100.0
    private var pn = 100.0
    private var pve = 10.0
    private var pvn = 10.0
    private var lastTimeMs = 0L

    fun reset() {
        initialized = false
        e = 0.0; n = 0.0; ve = 0.0; vn = 0.0
        pe = 100.0; pn = 100.0; pve = 10.0; pvn = 10.0
        lastTimeMs = 0L
    }

    fun update(lat: Double, lon: Double, accuracyMeters: Float, timestampMs: Long): Pair<Double, Double> {
        if (!initialized) {
            refLat = lat
            refLon = lon
            initialized = true
            lastTimeMs = timestampMs
            val r = (accuracyMeters.toDouble().coerceAtLeast(1.0)).let { it * it }
            pe = r; pn = r
            return Pair(lat, lon)
        }

        val dt = ((timestampMs - lastTimeMs).coerceIn(100L, 10_000L)) / 1000.0
        lastTimeMs = timestampMs

        // Predict
        e += ve * dt
        n += vn * dt
        pe += pve * dt * dt + processNoisePos * dt
        pn += pvn * dt * dt + processNoisePos * dt
        pve += processNoiseVel * dt
        pvn += processNoiseVel * dt

        // Measurement in local ENU
        val (measE, measN) = GnssMath.enuOffsetMeters(refLat, refLon, lat, lon)
        val r = (accuracyMeters.toDouble().coerceAtLeast(1.0)).let { it * it }

        // Kalman gain
        val ke = pe / (pe + r)
        val kn = pn / (pn + r)

        val innovE = measE - e
        val innovN = measN - n

        e += ke * innovE
        n += kn * innovN
        ve += (ke * innovE) / dt * 0.25
        vn += (kn * innovN) / dt * 0.25

        pe *= (1.0 - ke)
        pn *= (1.0 - kn)

        // Convert back to lat/lon
        val outLat = refLat + Math.toDegrees(n / GnssMath.EARTH_RADIUS_METERS)
        val outLon = refLon + Math.toDegrees(e / (GnssMath.EARTH_RADIUS_METERS * cos(Math.toRadians(refLat))))
        return Pair(outLat, outLon)
    }
}
