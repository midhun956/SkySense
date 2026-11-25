package com.skysense.app.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.GnssMeasurement
import android.location.GnssMeasurementRequest
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.skysense.app.data.model.*
import com.skysense.app.domain.GnssHardwareInspector
import com.skysense.app.domain.GnssMath
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core centralized GNSS data service.
 * Wraps LocationManager, GnssStatus, GnssMeasurementsEvent, GnssNavigationMessage, and NMEA
 * into reactive StateFlows and SharedFlows without redundant OS listeners.
 */
class GnssDataService(private val context: Context) {

    companion object {
        private const val TAG = "GnssDataService"
        private const val DEFAULT_MIN_TIME_MS = 1000L
        private const val MIN_DISTANCE_M = 0f
        private const val MAX_NAV_BUFFER = 120
        private const val MAX_NMEA_BUFFER = 120
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

    private val _isLocationEnabled = MutableStateFlow(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    // ── Lab / Scientific Flows ────────────────────────────────────────────────

    private val _rawClock = MutableStateFlow(RawClockData())
    val rawClock: StateFlow<RawClockData> = _rawClock.asStateFlow()

    private val _rawMeasurements = MutableStateFlow<List<RawMeasurementData>>(emptyList())
    val rawMeasurements: StateFlow<List<RawMeasurementData>> = _rawMeasurements.asStateFlow()

    private val _agcMeasurements = MutableStateFlow<List<AgcMeasurementData>>(emptyList())
    val agcMeasurements: StateFlow<List<AgcMeasurementData>> = _agcMeasurements.asStateFlow()

    private val _navMessages = MutableStateFlow<List<NavMessageData>>(emptyList())
    val navMessages: StateFlow<List<NavMessageData>> = _navMessages.asStateFlow()

    private val _nmeaMessages = MutableStateFlow<List<NmeaMessageData>>(emptyList())
    val nmeaMessages: StateFlow<List<NmeaMessageData>> = _nmeaMessages.asStateFlow()

    private val _nmeaGgaInfo = MutableStateFlow<NmeaGgaInfo?>(null)
    val nmeaGgaInfo: StateFlow<NmeaGgaInfo?> = _nmeaGgaInfo.asStateFlow()

    private val _mslAltitudeMeters = MutableStateFlow<Double?>(null)
    val mslAltitudeMeters: StateFlow<Double?> = _mslAltitudeMeters.asStateFlow()

    private val _hardwareCapabilities = MutableStateFlow(HardwareCapabilitiesReport())
    val hardwareCapabilities: StateFlow<HardwareCapabilitiesReport> = _hardwareCapabilities.asStateFlow()

    private val _receiverControlState = MutableStateFlow(ReceiverControlState())
    val receiverControlState: StateFlow<ReceiverControlState> = _receiverControlState.asStateFlow()

    /** Event stream for the Raw GNSS Data Recorder */
    private val _measurementEpochEvents = MutableSharedFlow<Pair<RawClockData, List<RawMeasurementData>>>(extraBufferCapacity = 32)
    val measurementEpochEvents: SharedFlow<Pair<RawClockData, List<RawMeasurementData>>> = _measurementEpochEvents.asSharedFlow()

    private val _nmeaEvents = MutableSharedFlow<NmeaMessageData>(extraBufferCapacity = 64)
    val nmeaEvents: SharedFlow<NmeaMessageData> = _nmeaEvents.asSharedFlow()

    private val _navEvents = MutableSharedFlow<NavMessageData>(extraBufferCapacity = 64)
    val navEvents: SharedFlow<NavMessageData> = _navEvents.asSharedFlow()

    // ── Internal tracking ─────────────────────────────────────────────────────

    private val measurementCn0Map = mutableMapOf<Pair<Int, Int>, Float>()
    private val measurementFreqMap = mutableMapOf<Pair<Int, Int>, Float>()
    private var lastLocation: Location? = null
    private var isStarted = false
    private var lastMeasurementEpochMs = 0L
    private var ttffStartMs: Long? = null

    // ── Callbacks ─────────────────────────────────────────────────────────────

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            _isReceivingUpdates.value = true

            // Check TTFF stopwatch
            ttffStartMs?.let { start ->
                val elapsed = SystemClock.elapsedRealtime() - start
                ttffStartMs = null
                _receiverControlState.value = _receiverControlState.value.copy(
                    ttffMillis = elapsed,
                    isMeasuringTtff = false,
                    statusMessage = "Fix acquired in $elapsed ms"
                )
            }

            // API 34+ MSL altitude
            if (Build.VERSION.SDK_INT >= 34) {
                runCatching {
                    val hasMsl = location.javaClass.getMethod("hasMslAltitude").invoke(location) as Boolean
                    if (hasMsl) {
                        val msl = location.javaClass.getMethod("getMslAltitudeMeters").invoke(location) as Double
                        _mslAltitudeMeters.value = msl
                    }
                }
            }

            rebuildSnapshot(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) _isLocationEnabled.value = true
        }
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) _isLocationEnabled.value = false
            _isReceivingUpdates.value = false
        }
    }

    private val providersChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                _isLocationEnabled.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }
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
            updateDopFromSatellites(sats)
            lastLocation?.let { rebuildSnapshot(it) }
        }
    }

    private val measurementCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
            val nowElapsedNanos = SystemClock.elapsedRealtimeNanos()
            val nowSystemMs = System.currentTimeMillis()

            val rateHz = if (lastMeasurementEpochMs > 0L) {
                val dt = (nowSystemMs - lastMeasurementEpochMs).coerceAtLeast(1L)
                (1000f / dt.toFloat()).coerceIn(0.1f, 20f)
            } else 1f
            lastMeasurementEpochMs = nowSystemMs

            val clk = event.clock
            val clockData = RawClockData(
                timeNanos = clk.timeNanos,
                hasFullBiasNanos = clk.hasFullBiasNanos(),
                fullBiasNanos = if (clk.hasFullBiasNanos()) clk.fullBiasNanos else 0L,
                hasBiasNanos = clk.hasBiasNanos(),
                biasNanos = if (clk.hasBiasNanos()) clk.biasNanos else 0.0,
                hasBiasUncertaintyNanos = clk.hasBiasUncertaintyNanos(),
                biasUncertaintyNanos = if (clk.hasBiasUncertaintyNanos()) clk.biasUncertaintyNanos else 0.0,
                hasDriftNanosPerSecond = clk.hasDriftNanosPerSecond(),
                driftNanosPerSecond = if (clk.hasDriftNanosPerSecond()) clk.driftNanosPerSecond else 0.0,
                hasDriftUncertaintyNanosPerSecond = clk.hasDriftUncertaintyNanosPerSecond(),
                driftUncertaintyNanosPerSecond = if (clk.hasDriftUncertaintyNanosPerSecond()) clk.driftUncertaintyNanosPerSecond else 0.0,
                hasLeapSecond = clk.hasLeapSecond(),
                leapSecond = if (clk.hasLeapSecond()) clk.leapSecond else 0,
                hasTimeUncertaintyNanos = clk.hasTimeUncertaintyNanos(),
                timeUncertaintyNanos = if (clk.hasTimeUncertaintyNanos()) clk.timeUncertaintyNanos else 0.0,
                hardwareClockDiscontinuityCount = clk.hardwareClockDiscontinuityCount,
                hasElapsedRealtimeNanos = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && clk.hasElapsedRealtimeNanos(),
                clockElapsedRealtimeNanos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && clk.hasElapsedRealtimeNanos()) clk.elapsedRealtimeNanos else 0L,
                receivedElapsedRealtimeNanos = nowElapsedNanos,
                receivedSystemTimeMs = nowSystemMs,
                isValid = true
            )
            _rawClock.value = clockData

            val rawList = ArrayList<RawMeasurementData>(event.measurements.size)
            for (m in event.measurements) {
                val key = Pair(m.svid, m.constellationType)
                if (m.cn0DbHz > 0) measurementCn0Map[key] = m.cn0DbHz.toFloat()
                if (m.hasCarrierFrequencyHz()) measurementFreqMap[key] = m.carrierFrequencyHz

                val pvt = extractSatellitePvt(m)
                rawList.add(RawMeasurementData(
                    svid = m.svid,
                    constellation = m.constellationType.toConstellationType(),
                    rawConstellationType = m.constellationType,
                    timeOffsetNanos = m.timeOffsetNanos,
                    state = m.state,
                    receivedSvTimeNanos = m.receivedSvTimeNanos,
                    receivedSvTimeUncertaintyNanos = m.receivedSvTimeUncertaintyNanos,
                    cn0DbHz = m.cn0DbHz,
                    hasBasebandCn0DbHz = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && m.hasBasebandCn0DbHz(),
                    basebandCn0DbHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && m.hasBasebandCn0DbHz()) m.basebandCn0DbHz else 0.0,
                    pseudorangeRateMetersPerSecond = m.pseudorangeRateMetersPerSecond,
                    pseudorangeRateUncertaintyMetersPerSecond = m.pseudorangeRateUncertaintyMetersPerSecond,
                    accumulatedDeltaRangeState = m.accumulatedDeltaRangeState,
                    accumulatedDeltaRangeMeters = m.accumulatedDeltaRangeMeters,
                    accumulatedDeltaRangeUncertaintyMeters = m.accumulatedDeltaRangeUncertaintyMeters,
                    hasCarrierFrequencyHz = m.hasCarrierFrequencyHz(),
                    carrierFrequencyHz = if (m.hasCarrierFrequencyHz()) m.carrierFrequencyHz else 0f,
                    multipathIndicator = m.multipathIndicator,
                    hasSnrInDb = m.hasSnrInDb(),
                    snrInDb = if (m.hasSnrInDb()) m.snrInDb else 0.0,
                    hasAutomaticGainControlLevelDb = @Suppress("DEPRECATION") m.hasAutomaticGainControlLevelDb(),
                    automaticGainControlLevelDb = if (@Suppress("DEPRECATION") m.hasAutomaticGainControlLevelDb()) @Suppress("DEPRECATION") m.automaticGainControlLevelDb else 0.0,
                    hasCodeType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && m.hasCodeType(),
                    codeType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && m.hasCodeType()) m.codeType else "",
                    hasFullInterSignalBiasNanos = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && m.hasFullInterSignalBiasNanos(),
                    fullInterSignalBiasNanos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && m.hasFullInterSignalBiasNanos()) m.fullInterSignalBiasNanos else 0.0,
                    satellitePvt = pvt,
                    receivedElapsedRealtimeNanos = nowElapsedNanos
                ))
            }
            _rawMeasurements.value = rawList
            _measurementEpochEvents.tryEmit(Pair(clockData, rawList))

            if (Build.VERSION.SDK_INT >= 33) {
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val agcList = event.javaClass.getMethod("getGnssAutomaticGainControls").invoke(event) as? Collection<Any>
                    if (!agcList.isNullOrEmpty()) {
                        val mapped = agcList.mapNotNull { agc ->
                            val cType = agc.javaClass.getMethod("getConstellationType").invoke(agc) as Int
                            val freq = agc.javaClass.getMethod("getCarrierFrequencyHz").invoke(agc) as Long
                            val level = agc.javaClass.getMethod("getLevelDb").invoke(agc) as Double
                            AgcMeasurementData(cType.toConstellationType(), freq, level, nowSystemMs)
                        }
                        if (mapped.isNotEmpty()) _agcMeasurements.value = mapped
                    }
                }
            }

            _receiverControlState.value = _receiverControlState.value.copy(measurementRateHz = rateHz)
            refreshHardwareCapabilities(rateHz)
        }
    }

    private val navMessageCallback = object : GnssNavigationMessage.Callback() {
        override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
            val bytes = event.data ?: ByteArray(0)
            val hex = bytes.joinToString("") { "%02X".format(it) }
            val msg = NavMessageData(
                svid = event.svid,
                type = event.type,
                typeLabel = navTypeToLabel(event.type),
                messageId = event.messageId,
                submessageId = event.submessageId,
                status = event.status,
                dataHex = hex,
                dataBytes = bytes,
                timestampMs = System.currentTimeMillis()
            )
            _navEvents.tryEmit(msg)
            _navMessages.value = (_navMessages.value + msg).takeLast(MAX_NAV_BUFFER)
        }
    }

    private val nmeaListener = OnNmeaMessageListener { message, timestamp ->
        val trimmed = message.trim()
        val data = NmeaMessageData(trimmed, timestamp)
        _nmeaEvents.tryEmit(data)
        _nmeaMessages.value = (_nmeaMessages.value + data).takeLast(MAX_NMEA_BUFFER)

        if (trimmed.startsWith("\$GPGGA") || trimmed.startsWith("\$GNGGA")) {
            parseNmeaGga(trimmed, timestamp)?.let { gga ->
                _nmeaGgaInfo.value = gga
                if (gga.mslAltitudeMeters != null && _mslAltitudeMeters.value == null) {
                    _mslAltitudeMeters.value = gga.mslAltitudeMeters
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
    fun start() {
        if (isStarted) return
        try {
            isStarted = true
            _isLocationEnabled.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ContextCompat.registerReceiver(
                context,
                providersChangedReceiver,
                IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
                ContextCompat.RECEIVER_EXPORTED
            )
            registerLocationAndMeasurementListeners()
            locationManager.registerGnssStatusCallback(gnssStatusCallback, mainHandler)
            locationManager.registerGnssNavigationMessageCallback(navMessageCallback, mainHandler)
            locationManager.addNmeaListener(nmeaListener, mainHandler)
            refreshHardwareCapabilities(0f)
            Log.d(TAG, "Centralized GNSS service started")
        } catch (e: Exception) {
            isStarted = false
            Log.e(TAG, "Failed to start GNSS service", e)
        }
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        try {
            context.unregisterReceiver(providersChangedReceiver)
        } catch (_: Exception) {}
        try {
            locationManager.removeUpdates(locationListener)
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
            locationManager.unregisterGnssMeasurementsCallback(measurementCallback)
            locationManager.unregisterGnssNavigationMessageCallback(navMessageCallback)
            locationManager.removeNmeaListener(nmeaListener)
            _isReceivingUpdates.value = false
            Log.d(TAG, "GNSS service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop GNSS service", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerLocationAndMeasurementListeners() {
        val ctrl = _receiverControlState.value
        val locInterval = if (ctrl.highPerformanceMode) 500L else DEFAULT_MIN_TIME_MS
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            locInterval,
            MIN_DISTANCE_M,
            locationListener,
            Looper.getMainLooper()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val req = GnssMeasurementRequest.Builder()
                .setFullTracking(ctrl.fullTrackingEnabled || ctrl.highPerformanceMode)
                .setIntervalMillis(if (ctrl.highPerformanceMode) 1000 else ctrl.measurementIntervalMs)
                .build()
            locationManager.registerGnssMeasurementsCallback(req, ContextCompat.getMainExecutor(context), measurementCallback)
        } else {
            locationManager.registerGnssMeasurementsCallback(measurementCallback, mainHandler)
        }
    }

    // ── Receiver Controls ─────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun applyReceiverControls(
        fullTracking: Boolean? = null,
        intervalMs: Int? = null,
        highPerformance: Boolean? = null
    ) {
        val current = _receiverControlState.value
        val next = current.copy(
            fullTrackingEnabled = fullTracking ?: current.fullTrackingEnabled,
            measurementIntervalMs = intervalMs ?: current.measurementIntervalMs,
            highPerformanceMode = highPerformance ?: current.highPerformanceMode,
            statusMessage = when {
                (highPerformance ?: current.highPerformanceMode) -> "High-performance continuous tracking active"
                (fullTracking ?: current.fullTrackingEnabled) -> "Full GNSS tracking enabled (duty-cycling off)"
                else -> "Standard OS-managed tracking (${intervalMs ?: current.measurementIntervalMs} ms)"
            }
        )
        _receiverControlState.value = next
        if (isStarted) {
            runCatching {
                locationManager.removeUpdates(locationListener)
                locationManager.unregisterGnssMeasurementsCallback(measurementCallback)
                registerLocationAndMeasurementListeners()
            }.onFailure {
                Log.e(TAG, "Failed to re-register with new receiver controls", it)
            }
        }
    }

    fun sendAgnssCommand(command: String): Boolean {
        val ok = runCatching {
            locationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, command, null)
        }.getOrDefault(false)
        _receiverControlState.value = _receiverControlState.value.copy(
            statusMessage = if (ok) "Command '$command' accepted by GNSS HAL" else "Command '$command' not supported by HAL"
        )
        return ok
    }

    @SuppressLint("MissingPermission")
    fun startTtffMeasurement() {
        ttffStartMs = SystemClock.elapsedRealtime()
        _receiverControlState.value = _receiverControlState.value.copy(
            isMeasuringTtff = true,
            ttffMillis = null,
            statusMessage = "Measuring Time-To-First-Fix…"
        )
        if (isStarted) {
            runCatching {
                locationManager.removeUpdates(locationListener)
                registerLocationAndMeasurementListeners()
            }
        }
    }

    fun refreshHardwareCapabilities(rateHz: Float = _receiverControlState.value.measurementRateHz) {
        _hardwareCapabilities.value = GnssHardwareInspector.inspect(
            locationManager = locationManager,
            satellites = _satellites.value,
            rawClock = _rawClock.value,
            rawMeasurements = _rawMeasurements.value,
            agcMeasurements = _agcMeasurements.value,
            navMessages = _navMessages.value,
            measurementRateHz = rateHz
        )
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

    private fun updateDopFromSatellites(satellites: List<SatelliteInfo>) {
        val usedSats = satellites.filter { it.usedInFix }
        val source = if (usedSats.size >= 4) usedSats else satellites
        val dop = GnssMath.computeTrueDop(source)
        if (dop.isValid) {
            _snapshot.value = _snapshot.value.copy(
                hdop = dop.hdop,
                vdop = dop.vdop,
                pdop = dop.pdop
            )
        }
    }

    private fun extractSatellitePvt(m: GnssMeasurement): SatellitePvtData? {
        if (Build.VERSION.SDK_INT < 34) return null
        return runCatching {
            val hasPvt = m.javaClass.getMethod("hasSatellitePvt").invoke(m) as Boolean
            if (!hasPvt) return@runCatching null
            val pvt = m.javaClass.getMethod("getSatellitePvt").invoke(m) ?: return@runCatching null
            val posEcef = pvt.javaClass.getMethod("getPositionEcef").invoke(pvt)
            val velEcef = pvt.javaClass.getMethod("getVelocityEcef").invoke(pvt)
            val clkInfo = pvt.javaClass.getMethod("getClockInfo").invoke(pvt)

            val px = posEcef?.javaClass?.getMethod("getXMeters")?.invoke(posEcef) as? Double ?: 0.0
            val py = posEcef?.javaClass?.getMethod("getYMeters")?.invoke(posEcef) as? Double ?: 0.0
            val pz = posEcef?.javaClass?.getMethod("getZMeters")?.invoke(posEcef) as? Double ?: 0.0
            val vx = velEcef?.javaClass?.getMethod("getXMetersPerSecond")?.invoke(velEcef) as? Double ?: 0.0
            val vy = velEcef?.javaClass?.getMethod("getYMetersPerSecond")?.invoke(velEcef) as? Double ?: 0.0
            val vz = velEcef?.javaClass?.getMethod("getZMetersPerSecond")?.invoke(velEcef) as? Double ?: 0.0
            val cb = clkInfo?.javaClass?.getMethod("getHardwareCodeBiasMeters")?.invoke(clkInfo) as? Double ?: 0.0
            val cd = clkInfo?.javaClass?.getMethod("getSatelliteClockDriftMps")?.invoke(clkInfo) as? Double ?: 0.0
            val iono = pvt.javaClass.getMethod("getIonoDelayMeters").invoke(pvt) as? Double ?: 0.0
            val tropo = pvt.javaClass.getMethod("getTropoDelayMeters").invoke(pvt) as? Double ?: 0.0

            SatellitePvtData(px, py, pz, vx, vy, vz, cb, cd, iono, tropo)
        }.getOrNull()
    }

    private fun parseNmeaGga(sentence: String, timestamp: Long): NmeaGgaInfo? {
        return runCatching {
            val parts = sentence.substringBefore("*").split(",")
            if (parts.size < 12) return@runCatching null
            val fixQuality = parts[6].toIntOrNull() ?: 0
            val hdop = parts[8].toFloatOrNull()
            val mslAlt = parts[9].toDoubleOrNull()
            val geoidSep = parts[11].toDoubleOrNull()
            NmeaGgaInfo(
                mslAltitudeMeters = mslAlt,
                geoidSeparationMeters = geoidSep,
                fixQuality = fixQuality,
                hdop = hdop,
                timestampMs = timestamp
            )
        }.getOrNull()
    }

    private fun navTypeToLabel(type: Int): String = when (type) {
        GnssNavigationMessage.TYPE_GPS_L1CA -> "GPS L1 C/A"
        GnssNavigationMessage.TYPE_GPS_L2CNAV -> "GPS L2 CNAV"
        GnssNavigationMessage.TYPE_GPS_L5CNAV -> "GPS L5 CNAV"
        GnssNavigationMessage.TYPE_GPS_CNAV2 -> "GPS L1C CNAV2"
        GnssNavigationMessage.TYPE_GLO_L1CA -> "GLONASS L1"
        GnssNavigationMessage.TYPE_BDS_D1 -> "BeiDou D1"
        GnssNavigationMessage.TYPE_BDS_D2 -> "BeiDou D2"
        GnssNavigationMessage.TYPE_GAL_I -> "Galileo I/NAV"
        GnssNavigationMessage.TYPE_GAL_F -> "Galileo F/NAV"
        else -> "Type $type"
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
