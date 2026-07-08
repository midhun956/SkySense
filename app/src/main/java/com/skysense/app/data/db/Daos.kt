package com.skysense.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GnssHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: GnssHistoryEntity)

    @Query("SELECT * FROM gnss_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSnapshots(limit: Int = 200): Flow<List<GnssHistoryEntity>>

    @Query("SELECT * FROM gnss_history WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getSnapshotsSince(since: Long): Flow<List<GnssHistoryEntity>>

    @Query("SELECT COUNT(*) FROM gnss_history")
    suspend fun getCount(): Int

    @Query("DELETE FROM gnss_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM gnss_history")
    suspend fun clearAll()
}

@Dao
interface SatelliteHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SatelliteHistoryEntity>)

    @Query("SELECT * FROM satellite_history WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getHistorySince(since: Long): Flow<List<SatelliteHistoryEntity>>

    @Query("DELETE FROM satellite_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM satellite_history")
    suspend fun clearAll()
}
