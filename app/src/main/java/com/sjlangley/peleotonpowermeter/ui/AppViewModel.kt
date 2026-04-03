package com.sjlangley.peleotonpowermeter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sjlangley.peleotonpowermeter.data.model.AppScreen
import com.sjlangley.peleotonpowermeter.data.model.AppUiState
import com.sjlangley.peleotonpowermeter.data.model.ConnectionState
import com.sjlangley.peleotonpowermeter.data.model.PreviewRideData
import com.sjlangley.peleotonpowermeter.data.model.SetupDeviceState
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.domain.RideSummaryCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class AppViewModel(
    private val rideStore: RideStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PreviewRideData.appState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val rideFlowMutex = Mutex()
    private var currentRideId: String? = null
    private var includePedalDropout = false

    suspend fun onSetupPrimaryAction() {
        rideFlowMutex.withLock {
            val current = _uiState.value
            if (current.currentScreen != AppScreen.SETUP) {
                return
            }

            if (!current.setup.canStartRide) {
                _uiState.update { setupState -> setupState.withHeartRateReady() }
                return
            }

            if (currentRideId != null) {
                return
            }

            val rideId = nextRideId()
            val initialSamples = PreviewRideData.initialLiveSamples()
            rideStore.startSession(PreviewRideData.demoRideSession(rideId))
            rideStore.appendSamples(rideId, initialSamples)

            currentRideId = rideId
            includePedalDropout = false
            _uiState.update { liveState -> liveState.asLiveRideState() }
        }
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

    suspend fun onLivePrimaryAction() {
        rideFlowMutex.withLock {
            val rideId = checkNotNull(currentRideId) { "A ride must be started before it can be finished." }
            val completeRideSamples = PreviewRideData.demoRideSamples(includePedalDropout = includePedalDropout)
            val persistedSamples = rideStore.loadSamples(rideId)
            rideStore.appendSamples(rideId, completeRideSamples.drop(persistedSamples.size))
            rideStore.finishSession(rideId, completeRideSamples.last().timestampEpochSeconds)

            val derivedSummary = RideSummaryCalculator.calculate(completeRideSamples)
            rideStore.saveSummary(rideId, derivedSummary)

            val storedSamples = rideStore.loadSamples(rideId)
            val storedSummary = checkNotNull(rideStore.loadSummary(rideId))

            _uiState.update { current ->
                current.copy(
                    currentScreen = AppScreen.SUMMARY,
                    summary = SummaryUiStateFactory.fromRideData(storedSamples, storedSummary),
                )
            }
        }
    }

    fun onLiveSecondaryAction() {
        _uiState.update { current ->
            val currentlyDegraded = current.live.truthStrip != null
            includePedalDropout = !currentlyDegraded
            current.copy(
                live = current.live.copy(
                    powerWatts = if (currentlyDegraded) 214 else 286,
                    cadenceRpm = if (currentlyDegraded) 88 else 92,
                    heartRateBpm = if (currentlyDegraded) 148 else 154,
                    zoneLabel = if (currentlyDegraded) "Zone 3" else "Zone 4",
                    zoneProgress = if (currentlyDegraded) 0.48f else 0.62f,
                    truthStrip =
                        if (currentlyDegraded) {
                            null
                        } else {
                            "Left pedal disconnected. Recording continues. Balance is partial."
                        },
                    secondaryActionLabel = if (currentlyDegraded) "Simulate Pedal Dropout" else "Restore Sensors",
                ),
            )
        }
    }

    fun onSummaryReset() {
        currentRideId = null
        includePedalDropout = false
        _uiState.value = PreviewRideData.appState()
    }

    private fun nextRideId(): String = "demo-ride-${UUID.randomUUID()}"

    companion object {
        fun factory(rideStore: RideStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(rideStore) as T
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

private fun AppUiState.asLiveRideState(): AppUiState =
    copy(
        currentScreen = AppScreen.LIVE,
        live = live.copy(
            elapsedLabel = "00:12",
            powerWatts = 214,
            cadenceRpm = 88,
            heartRateBpm = 148,
            zoneLabel = "Zone 3",
            zoneProgress = 0.48f,
            truthStrip = null,
            primaryActionLabel = "Finish Ride",
            secondaryActionLabel = "Simulate Pedal Dropout",
        ),
    )
