package com.sjlangley.peleotonpowermeter.data.model

data class RideSession(
    val rideId: String,
    val startedAtEpochSeconds: Long,
    val endedAtEpochSeconds: Long?,
    val ftpWatts: Int,
    val pedalPair: PedalPair,
    val heartRateSource: HeartRateSource,
    val syncState: SyncState,
    val interruptionDetected: Boolean,
)
