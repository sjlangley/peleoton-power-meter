package com.sjlangley.peleotonpowermeter.recorder

import kotlinx.coroutines.flow.StateFlow

interface RecorderSessionController {
    val sessionState: StateFlow<RecorderSessionState>

    /**
     * Start a ride recording session.
     * For demo controllers, this starts a simulated ride.
     * For BLE controllers, this connects to real devices and starts recording.
     */
    suspend fun startRide()

    /**
     * Start a demo ride with simulated data.
     * @deprecated Use startRide() instead for polymorphic controller usage.
     */
    @Deprecated("Use startRide() for polymorphic usage")
    suspend fun startDemoRide()

    suspend fun togglePedalDropout()
    suspend fun finishRide()
    fun reset()
}

sealed interface RecorderSessionState {
    data object Idle : RecorderSessionState

    data class Active(
        val rideId: String,
        val liveFrame: RecorderLiveFrame,
    ) : RecorderSessionState

    data class Completed(
        val rideId: String,
    ) : RecorderSessionState
}

data class RecorderLiveFrame(
    val elapsedLabel: String,
    val powerWatts: Int,
    val cadenceRpm: Int?,
    val heartRateBpm: Int?,
    val zoneLabel: String,
    val zoneProgress: Float,
    val truthStrip: String?,
    val secondaryActionLabel: String,
)
