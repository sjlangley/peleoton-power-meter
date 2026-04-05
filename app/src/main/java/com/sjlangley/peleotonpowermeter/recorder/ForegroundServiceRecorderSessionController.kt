package com.sjlangley.peleotonpowermeter.recorder

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

class ForegroundServiceRecorderSessionController(
    private val context: Context,
    private val sessionStateStore: RecorderSessionStateStore,
) : RecorderSessionController {
    override val sessionState: StateFlow<RecorderSessionState> = sessionStateStore.sessionState

    override suspend fun startDemoRide() {
        context.startForegroundService(RideRecorderService.startIntent(context))
    }

    override suspend fun togglePedalDropout() {
        context.startService(RideRecorderService.toggleDropoutIntent(context))
    }

    override suspend fun finishRide() {
        context.startService(RideRecorderService.finishIntent(context))
    }

    override fun reset() {
        sessionStateStore.reset()
    }
}
