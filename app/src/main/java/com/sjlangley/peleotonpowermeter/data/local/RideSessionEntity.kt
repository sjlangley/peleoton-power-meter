package com.sjlangley.peleotonpowermeter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sjlangley.peleotonpowermeter.data.model.SyncState

@Entity(tableName = "ride_sessions")
data class RideSessionEntity(
    @PrimaryKey val rideId: String,
    val startedAtEpochSeconds: Long,
    val endedAtEpochSeconds: Long?,
    val ftpWatts: Int,
    val leftDeviceId: String?,
    val rightDeviceId: String?,
    val heartRateDeviceId: String?,
    val syncState: SyncState,
    val interruptionDetected: Boolean,
)
