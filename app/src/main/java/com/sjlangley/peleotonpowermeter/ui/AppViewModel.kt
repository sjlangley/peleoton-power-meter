package com.sjlangley.peleotonpowermeter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sjlangley.peleotonpowermeter.data.model.AppScreen
import com.sjlangley.peleotonpowermeter.data.model.AppUiState
import com.sjlangley.peleotonpowermeter.data.model.PreviewRideData
import com.sjlangley.peleotonpowermeter.data.model.SyncState
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.recorder.RecorderLiveFrame
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionController
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionState
import com.sjlangley.peleotonpowermeter.setup.RememberedDevice
import com.sjlangley.peleotonpowermeter.setup.RememberedDeviceStore
import com.sjlangley.peleotonpowermeter.setup.RememberedDevices
import com.sjlangley.peleotonpowermeter.setup.SetupDeviceRole
import com.sjlangley.peleotonpowermeter.setup.SetupUiStateFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    private val rememberedDeviceStore: RememberedDeviceStore,
    private val rideStore: RideStore,
    recorderSessionController: RecorderSessionController,
) : ViewModel() {
    private var rememberedDevices: RememberedDevices = rememberedDeviceStore.loadRememberedDevices()
    private var pendingAssociationRole: SetupDeviceRole? = null

    private val _uiState =
        MutableStateFlow(
            AppUiState(
                currentScreen = AppScreen.SETUP,
                setup = SetupUiStateFactory.fromRememberedDevices(rememberedDevices),
                live = PreviewRideData.liveRideState(),
                summary = PreviewRideData.summaryState(),
            ),
        )
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
        if (current.currentScreen != AppScreen.SETUP || !current.setup.canStartRide) {
            return
        }

        _uiState.update { liveState -> liveState.asLiveRidePendingState() }
    }

    fun onSetupAssociationRequested(role: SetupDeviceRole) {
        pendingAssociationRole = role
        renderSetupState()
    }

    fun onSetupAssociationSucceeded(
        role: SetupDeviceRole,
        rememberedDevice: RememberedDevice,
    ) {
        if (pendingAssociationRole != role) return
        rememberedDeviceStore.rememberDevice(role, rememberedDevice)
        rememberedDevices = rememberedDevices.update(role, rememberedDevice)
        pendingAssociationRole = null
        renderSetupState()
    }

    fun onSetupAssociationFailed() {
        pendingAssociationRole = null
        renderSetupState()
    }

    fun onSetupSecondaryAction() {
        rememberedDeviceStore.clearRememberedDevices()
        rememberedDevices = RememberedDevices()
        pendingAssociationRole = null
        renderSetupState()
    }

    fun onSummaryReset() {
        pendingAssociationRole = null
        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.SETUP,
                live = PreviewRideData.liveRideState(),
                summary = PreviewRideData.summaryState(),
            )
        }
        renderSetupState()
    }

    suspend fun onSummaryExportStateChanged(
        rideId: String,
        exportState: SyncState,
    ) {
        val storedSummary = rideStore.loadSummary(rideId) ?: return
        rideStore.saveSummary(rideId, storedSummary.copy(exportState = exportState))
        loadSummaryForRide(rideId)
    }

    fun nextAssociationRole(): SetupDeviceRole? = rememberedDevices.nextMissingRole()

    fun pendingAssociationRole(): SetupDeviceRole? = pendingAssociationRole

    fun rememberedDevices(): RememberedDevices = rememberedDevices

    fun isAssociationPending(): Boolean = pendingAssociationRole != null

    private fun renderSetupState() {
        _uiState.update { current ->
            current.copy(
                setup = SetupUiStateFactory.fromRememberedDevices(rememberedDevices, pendingAssociationRole),
            )
        }
    }

    companion object {
        fun factory(
            rememberedDeviceStore: RememberedDeviceStore,
            rideStore: RideStore,
            recorderSessionController: RecorderSessionController,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(rememberedDeviceStore, rideStore, recorderSessionController) as T
            }
    }

    private suspend fun loadSummaryForRide(rideId: String) {
        val storedSamples = rideStore.loadSamples(rideId)
        val storedSummary = rideStore.loadSummary(rideId) ?: return

        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.SUMMARY,
                summary = SummaryUiStateFactory.fromRideData(rideId, storedSamples, storedSummary),
            )
        }
    }
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
