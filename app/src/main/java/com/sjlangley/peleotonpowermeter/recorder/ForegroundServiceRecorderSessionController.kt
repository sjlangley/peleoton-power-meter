package com.sjlangley.peleotonpowermeter.recorder

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

class ForegroundServiceRecorderSessionController(
    private val context: Context,
    private val sessionStateStore: RecorderSessionStateStore,
) : RecorderSessionController {
    override val sessionState: StateFlow<RecorderSessionState> = sessionStateStore.sessionState

    override suspend fun startDemoRide() {
        startRecorderCommand(RideRecorderService.startIntent(context))
    }

    override suspend fun togglePedalDropout() {
        startRecorderCommand(RideRecorderService.toggleDropoutIntent(context))
    }

    override suspend fun finishRide() {
        startRecorderCommand(RideRecorderService.finishIntent(context))
    }

    override fun reset() {
        sessionStateStore.reset()
    }

    private fun startRecorderCommand(intent: android.content.Intent) {
        context.startForegroundService(intent)
    }
}
