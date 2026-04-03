package com.sjlangley.peleotonpowermeter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sjlangley.peleotonpowermeter.data.model.AsymmetryInterval
import com.sjlangley.peleotonpowermeter.data.model.SyncState

@Entity(tableName = "derived_summaries")
data class DerivedSummaryEntity(
    @PrimaryKey val rideId: String,
    val averagePowerWatts: Int,
    val maxPowerWatts: Int,
    val averageCadenceRpm: Int?,
    val averageHeartRateBpm: Int?,
    val averageBalancePercentLeft: Int?,
    val timeInZoneSeconds: Map<Int, Int>,
    val asymmetryIntervals: List<AsymmetryInterval>,
    val partialHeartRate: Boolean,
    val partialBalance: Boolean,
    val exportState: SyncState,
)
