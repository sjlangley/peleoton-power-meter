package com.sjlangley.peleotonpowermeter.testutil

import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionController
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeRecorderSessionController : RecorderSessionController {
    private val _sessionState = MutableStateFlow<RecorderSessionState>(RecorderSessionState.Idle)

    override val sessionState: StateFlow<RecorderSessionState> = _sessionState.asStateFlow()

    var startCalls = 0
    var finishCalls = 0
    var toggleCalls = 0
    var resetCalls = 0

    override suspend fun startDemoRide() {
        startCalls += 1
    }

    override suspend fun togglePedalDropout() {
        toggleCalls += 1
    }

    override suspend fun finishRide() {
        finishCalls += 1
    }

    override fun reset() {
        resetCalls += 1
        _sessionState.value = RecorderSessionState.Idle
    }

    fun emit(state: RecorderSessionState) {
        _sessionState.value = state
    }
}
