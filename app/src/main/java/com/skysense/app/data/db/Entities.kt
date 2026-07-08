package com.skysense.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Room entity for storing periodic GNSS snapshots for history/analytics.
 */
@Entity(tableName = "gnss_history")
data class GnssHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val horizontalAccuracy: Float,
    val verticalAccuracy: Float,
    val speed: Float,
    val bearing: Float,
    val satellitesUsed: Int,
    val satellitesVisible: Int,
    val constellationsUsed: String,   // Comma-separated constellation names
    val pdop: Float,
    val hdop: Float,
    val vdop: Float
)

/**
 * Room entity for storing per-satellite signal history.
 */
@Entity(tableName = "satellite_history")
data class SatelliteHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val svid: Int,
    val constellation: String,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean
)
