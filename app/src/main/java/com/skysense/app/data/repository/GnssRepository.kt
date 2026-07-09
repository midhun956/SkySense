package com.skysense.app.data.repository

import com.skysense.app.data.db.GnssHistoryDao
import com.skysense.app.data.db.GnssHistoryEntity
import com.skysense.app.data.db.SatelliteHistoryDao
import com.skysense.app.data.db.SatelliteHistoryEntity
import com.skysense.app.data.model.GnssSnapshot
import com.skysense.app.data.model.SatelliteInfo
import com.skysense.app.service.GnssDataService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Repository combining live GNSS data (from GnssDataService) with persistence (Room).
 * Periodically saves snapshots to the database for history/analytics.
 */
class GnssRepository(
    private val gnssDataService: GnssDataService,
    private val gnssHistoryDao: GnssHistoryDao,
    private val satelliteHistoryDao: SatelliteHistoryDao
) {
    val liveSnapshot: StateFlow<GnssSnapshot> = gnssDataService.snapshot
    val liveSatellites: StateFlow<List<SatelliteInfo>> = gnssDataService.satellites
    val isReceivingUpdates: StateFlow<Boolean> = gnssDataService.isReceivingUpdates
    val isLocationEnabled: StateFlow<Boolean> = gnssDataService.isLocationEnabled

    private var recordingJob: Job? = null

    fun startGnss() = gnssDataService.start()
    fun stopGnss() = gnssDataService.stop()

    /** Begin auto-saving snapshots every [intervalMs] ms (default 30s). */
    fun startRecording(
        scope: CoroutineScope,
        intervalMs: Long = 30_000L
    ) {
        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(intervalMs)
                val snap = liveSnapshot.value
                if (snap.hasValidFix) {
                    saveSnapshot(snap)
                    val sats = liveSatellites.value
                    if (sats.isNotEmpty()) saveSatellites(sats, snap.timestamp)
                }
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
    }

    private suspend fun saveSnapshot(s: GnssSnapshot) {
        gnssHistoryDao.insertSnapshot(GnssHistoryEntity(
            timestamp = s.timestamp,
            latitude = s.latitude,
            longitude = s.longitude,
            altitude = s.altitude,
            horizontalAccuracy = s.horizontalAccuracy,
            verticalAccuracy = s.verticalAccuracy,
            speed = s.speed,
            bearing = s.bearing,
            satellitesUsed = s.satellitesUsed,
            satellitesVisible = s.satellitesVisible,
            constellationsUsed = s.constellationsUsed.joinToString(",") { it.name },
            pdop = s.pdop,
            hdop = s.hdop,
            vdop = s.vdop
        ))
    }

    private suspend fun saveSatellites(sats: List<SatelliteInfo>, timestamp: Long) {
        satelliteHistoryDao.insertAll(sats.map { sat ->
            SatelliteHistoryEntity(
                timestamp = timestamp,
                svid = sat.svid,
                constellation = sat.constellation.name,
                cn0DbHz = sat.cn0DbHz,
                elevationDegrees = sat.elevationDegrees,
                azimuthDegrees = sat.azimuthDegrees,
                usedInFix = sat.usedInFix
            )
        })
    }

    // ── History queries ───────────────────────────────────────────────────────

    fun getSnapshotsSince(since: Long): Flow<List<GnssHistoryEntity>> =
        gnssHistoryDao.getSnapshotsSince(since)

    fun getRecentSnapshots(limit: Int = 200): Flow<List<GnssHistoryEntity>> =
        gnssHistoryDao.getRecentSnapshots(limit)

    suspend fun pruneOldData(retentionMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - retentionMs
        gnssHistoryDao.deleteOlderThan(cutoff)
        satelliteHistoryDao.deleteOlderThan(cutoff)
    }

    suspend fun clearAllHistory() {
        gnssHistoryDao.clearAll()
        satelliteHistoryDao.clearAll()
    }
}
