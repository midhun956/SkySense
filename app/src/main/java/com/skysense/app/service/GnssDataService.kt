package com.skysense.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.skysense.app.data.model.ConstellationType
import com.skysense.app.data.model.GnssSnapshot
import com.skysense.app.data.model.SatelliteInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Core GNSS data service. Wraps LocationManager + GnssStatus + GnssMeasurements
 * and exposes reactive StateFlows consumed by ViewModels.
 *
 * Must be started/stopped from a lifecycle-aware component.
 */
class GnssDataService(private val context: Context) {

    companion object {
        private const val TAG = "GnssDataService"
        private const val MIN_TIME_MS = 1000L
        private const val MIN_DISTANCE_M = 0f
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Public State Flows ────────────────────────────────────────────────────

    private val _snapshot = MutableStateFlow(GnssSnapshot())
    val snapshot: StateFlow<GnssSnapshot> = _snapshot.asStateFlow()

    private val _satellites = MutableStateFlow<List<SatelliteInfo>>(emptyList())
    val satellites: StateFlow<List<SatelliteInfo>> = _satellites.asStateFlow()

    private val _isReceivingUpdates = MutableStateFlow(false)
    val isReceivingUpdates: StateFlow<Boolean> = _isReceivingUpdates.asStateFlow()

    // ── Internal tracking ─────────────────────────────────────────────────────

    // cn0 map from measurements: svid+constellation → cn0
    private val measurementCn0Map = mutableMapOf<Pair<Int, Int>, Float>()
    // Carrier frequency map from measurements: svid+constellation → hz
    private val measurementFreqMap = mutableMapOf<Pair<Int, Int>, Float>()
    private var lastLocation: Location? = null

    // ── Callbacks ─────────────────────────────────────────────────────────────

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            _isReceivingUpdates.value = true
            rebuildSnapshot(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            _isReceivingUpdates.value = false
        }
    }

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val sats = mutableListOf<SatelliteInfo>()
            for (i in 0 until status.satelliteCount) {
                val svid = status.getSvid(i)
                val constellation = status.getConstellationType(i).toConstellationType()
                val key = Pair(svid, status.getConstellationType(i))
                val cn0 = if (status.getCn0DbHz(i) > 0f) status.getCn0DbHz(i)
                           else measurementCn0Map[key] ?: 0f
                val hasFreq = status.hasCarrierFrequencyHz(i)
                val freq = if (hasFreq) status.getCarrierFrequencyHz(i) else measurementFreqMap[key] ?: 0f
                sats.add(SatelliteInfo(
                    svid = svid,
                    constellation = constellation,
                    elevationDegrees = status.getElevationDegrees(i),
                    azimuthDegrees = status.getAzimuthDegrees(i),
                    cn0DbHz = cn0,
                    usedInFix = status.usedInFix(i),
                    hasCarrierFrequency = hasFreq || measurementFreqMap.containsKey(key),
                    carrierFrequencyHz = freq
                ))
            }
            _satellites.value = sats
            // Rebuild snapshot with updated satellite counts
            lastLocation?.let { rebuildSnapshot(it) }
        }
    }

    private val measurementCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
            // Cache C/N₀ and carrier freq from raw measurements for richer data
            for (m in event.measurements) {
                val key = Pair(m.svid, m.constellationType)
                if (m.cn0DbHz > 0) measurementCn0Map[key] = m.cn0DbHz.toFloat()
                if (m.hasCarrierFrequencyHz()) measurementFreqMap[key] = m.carrierFrequencyHz
            }
            // Estimate DOP from measurement geometry
            estimateAndUpdateDop(event)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun start() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_MS,
                MIN_DISTANCE_M,
                locationListener,
                Looper.getMainLooper()
            )
            locationManager.registerGnssStatusCallback(gnssStatusCallback, mainHandler)
            locationManager.registerGnssMeasurementsCallback(measurementCallback, mainHandler)
            Log.d(TAG, "GNSS service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GNSS service", e)
        }
    }

    fun stop() {
        try {
            locationManager.removeUpdates(locationListener)
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
            locationManager.unregisterGnssMeasurementsCallback(measurementCallback)
            _isReceivingUpdates.value = false
            Log.d(TAG, "GNSS service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop GNSS service", e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun rebuildSnapshot(location: Location) {
        val sats = _satellites.value
        val used = sats.count { it.usedInFix }
        val visible = sats.size
        val constellations = sats.filter { it.usedInFix }
            .map { it.constellation }
            .toSet()
            .filter { it != ConstellationType.UNKNOWN }
            .toSet()

        _snapshot.value = GnssSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            horizontalAccuracy = location.accuracy,
            verticalAccuracy = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else 0f,
            speed = if (location.hasSpeed()) location.speed else 0f,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            satellitesUsed = used,
            satellitesVisible = visible,
            constellationsUsed = constellations,
            pdop = _snapshot.value.pdop,
            hdop = _snapshot.value.hdop,
            vdop = _snapshot.value.vdop,
            timestamp = location.time,
            hasValidFix = true
        )
    }

    /**
     * Simplified DOP estimation using satellite elevation geometry.
     * True DOP requires a full covariance matrix inversion; this approximation
     * is useful for educational display purposes.
     */
    private fun estimateAndUpdateDop(event: GnssMeasurementsEvent) {
        val usedSats = _satellites.value.filter { it.usedInFix }
        if (usedSats.size < 4) return

        // Simplified HDOP approximation: based on average elevation of used satellites
        val avgElevation = usedSats.map { it.elevationDegrees }.average().toFloat()
        val hdop = when {
            avgElevation > 60f -> 1.0f + (90f - avgElevation) / 90f
            avgElevation > 30f -> 1.5f + (60f - avgElevation) / 60f
            else -> 3.0f
        }
        val vdop = hdop * 1.3f
        val pdop = sqrt((hdop * hdop + vdop * vdop).toDouble()).toFloat()

        _snapshot.value = _snapshot.value.copy(
            hdop = hdop,
            vdop = vdop,
            pdop = pdop
        )
    }

    private fun Int.toConstellationType(): ConstellationType = when (this) {
        GnssStatus.CONSTELLATION_GPS -> ConstellationType.GPS
        GnssStatus.CONSTELLATION_GLONASS -> ConstellationType.GLONASS
        GnssStatus.CONSTELLATION_GALILEO -> ConstellationType.GALILEO
        GnssStatus.CONSTELLATION_BEIDOU -> ConstellationType.BEIDOU
        GnssStatus.CONSTELLATION_QZSS -> ConstellationType.QZSS
        GnssStatus.CONSTELLATION_IRNSS -> ConstellationType.IRNSS
        GnssStatus.CONSTELLATION_SBAS -> ConstellationType.SBAS
        else -> ConstellationType.UNKNOWN
    }
}
