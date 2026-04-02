package com.sjlangley.peleotonpowermeter.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ride_samples",
    indices = [Index("rideId")],
)
data class RideSampleEntity(
    @PrimaryKey(autoGenerate = true) val sampleId: Long = 0,
    val rideId: String,
    val timestampEpochSeconds: Long,
    val leftPowerWatts: Int?,
    val rightPowerWatts: Int?,
    val totalPowerWatts: Int,
    val cadenceRpm: Int?,
    val heartRateBpm: Int?,
    val zoneIndex: Int,
    val leftConnected: Boolean,
    val rightConnected: Boolean,
    val heartRateConnected: Boolean,
)
