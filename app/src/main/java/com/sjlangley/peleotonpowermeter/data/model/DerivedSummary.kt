package com.sjlangley.peleotonpowermeter.data.model

data class DerivedSummary(
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
