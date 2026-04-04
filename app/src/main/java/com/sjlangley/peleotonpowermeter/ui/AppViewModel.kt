package com.sjlangley.peleotonpowermeter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sjlangley.peleotonpowermeter.data.model.AppScreen
import com.sjlangley.peleotonpowermeter.data.model.AppUiState
import com.sjlangley.peleotonpowermeter.data.model.ConnectionState
import com.sjlangley.peleotonpowermeter.data.model.PreviewRideData
import com.sjlangley.peleotonpowermeter.data.model.SetupDeviceState
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.recorder.RecorderLiveFrame
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionController
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AppViewModel(
    private val rideStore: RideStore,
    recorderSessionController: RecorderSessionController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PreviewRideData.appState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            recorderSessionController.sessionState.collect { sessionState ->
                when (sessionState) {
                    RecorderSessionState.Idle -> Unit
                    is RecorderSessionState.Active -> {
                        _uiState.update { current ->
                            current.copy(
                                currentScreen = AppScreen.LIVE,
                                live = current.live.fromRecorderFrame(sessionState.liveFrame),
                            )
                        }
                    }

                    is RecorderSessionState.Completed -> loadSummaryForRide(sessionState.rideId)
                }
            }
        }
    }

    suspend fun onSetupPrimaryAction() {
        val current = _uiState.value
        if (current.currentScreen != AppScreen.SETUP) {
            return
        }

        if (!current.setup.canStartRide) {
            _uiState.update { setupState -> setupState.withHeartRateReady() }
            return
        }

        _uiState.update { liveState -> liveState.asLiveRidePendingState() }
    }

    fun onSetupSecondaryAction() {
        _uiState.update { current ->
            val shouldDisconnect = current.setup.canStartRide
            val updatedDevices =
                current.setup.devices.map {
                    if (it.label == "Heart Rate") {
                        SetupDeviceState(
                            label = it.label,
                            statusLabel = if (shouldDisconnect) "Searching" else "Connected",
                            state = if (shouldDisconnect) ConnectionState.SEARCHING else ConnectionState.CONNECTED,
                        )
                    } else {
                        it
                    }
                }

            current.copy(
                setup = current.setup.copy(
                    devices = updatedDevices,
                    overallStatus = if (shouldDisconnect) "Waiting for heart-rate monitor" else "All sensors ready",
                    primaryActionLabel = if (shouldDisconnect) "Continue Pairing" else "Start Demo Ride",
                    canStartRide = !shouldDisconnect,
                    secondaryActionLabel = if (shouldDisconnect) "Restore HR" else "Simulate Missing HR",
                ),
            )
        }
    }

    fun onSummaryReset() {
        _uiState.value = PreviewRideData.appState()
    }

    companion object {
        fun factory(
            rideStore: RideStore,
            recorderSessionController: RecorderSessionController,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(rideStore, recorderSessionController) as T
            }
    }

    private suspend fun loadSummaryForRide(rideId: String) {
        val storedSamples = rideStore.loadSamples(rideId)
        val storedSummary = rideStore.loadSummary(rideId) ?: return

        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.SUMMARY,
                summary = SummaryUiStateFactory.fromRideData(storedSamples, storedSummary),
            )
        }
    }
}

private fun AppUiState.withHeartRateReady(): AppUiState {
    val readyDevices =
        setup.devices.map { device ->
            if (device.label == "Heart Rate") {
                SetupDeviceState(
                    label = device.label,
                    statusLabel = "Connected",
                    state = ConnectionState.CONNECTED,
                )
            } else {
                device
            }
        }

    return copy(
        setup = setup.copy(
            devices = readyDevices,
            overallStatus = "All sensors ready",
            primaryActionLabel = "Start Demo Ride",
            canStartRide = true,
            secondaryActionLabel = "Simulate Missing HR",
        ),
    )
}

private fun AppUiState.asLiveRidePendingState(): AppUiState =
    copy(
        currentScreen = AppScreen.LIVE,
        live = live.copy(
            elapsedLabel = "00:00",
            powerWatts = 0,
            cadenceRpm = null,
            heartRateBpm = null,
            zoneLabel = "Starting",
            zoneProgress = 0f,
            truthStrip = null,
            primaryActionLabel = "Finish Ride",
            secondaryActionLabel = "Simulate Pedal Dropout",
        ),
    )

private fun com.sjlangley.peleotonpowermeter.data.model.LiveRideUiState.fromRecorderFrame(
    frame: RecorderLiveFrame,
): com.sjlangley.peleotonpowermeter.data.model.LiveRideUiState =
    copy(
        elapsedLabel = frame.elapsedLabel,
        powerWatts = frame.powerWatts,
        cadenceRpm = frame.cadenceRpm,
        heartRateBpm = frame.heartRateBpm,
        zoneLabel = frame.zoneLabel,
        zoneProgress = frame.zoneProgress,
        truthStrip = frame.truthStrip,
        primaryActionLabel = "Finish Ride",
        secondaryActionLabel = frame.secondaryActionLabel,
    )
