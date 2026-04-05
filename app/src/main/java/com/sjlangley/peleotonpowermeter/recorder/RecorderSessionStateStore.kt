package com.sjlangley.peleotonpowermeter.recorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecorderSessionStateStore {
    private val _sessionState = MutableStateFlow<RecorderSessionState>(RecorderSessionState.Idle)

    val sessionState: StateFlow<RecorderSessionState> = _sessionState.asStateFlow()

    fun publish(state: RecorderSessionState) {
        _sessionState.value = state
    }

    fun reset() {
        if (_sessionState.value is RecorderSessionState.Completed) {
            _sessionState.value = RecorderSessionState.Idle
        }
    }
}
