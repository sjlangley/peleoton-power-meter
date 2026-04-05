package com.sjlangley.peleotonpowermeter.ui

import android.os.Looper
import com.sjlangley.peleotonpowermeter.data.model.AppScreen
import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation
import com.sjlangley.peleotonpowermeter.data.model.PreviewRideData
import com.sjlangley.peleotonpowermeter.data.model.SyncState
import com.sjlangley.peleotonpowermeter.domain.RideSummaryCalculator
import com.sjlangley.peleotonpowermeter.recorder.RecorderLiveFrame
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionState
import com.sjlangley.peleotonpowermeter.setup.RememberedDevice
import com.sjlangley.peleotonpowermeter.setup.RememberedDevices
import com.sjlangley.peleotonpowermeter.setup.SetupDeviceRole
import com.sjlangley.peleotonpowermeter.testutil.FakeRecorderSessionController
import com.sjlangley.peleotonpowermeter.testutil.FakeRememberedDeviceStore
import com.sjlangley.peleotonpowermeter.testutil.FakeRideStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppViewModelTest {
    @Test
    fun initLoadsRememberedSetupState() {
        val viewModel =
            AppViewModel(
                rememberedDeviceStore =
                    FakeRememberedDeviceStore(
                        initialDevices =
                            RememberedDevices(
                                leftPedal = rememberedDevice(1, "Left Assioma", "left-id"),
                            ),
                    ),
                rideStore = FakeRideStore(),
                recorderSessionController = FakeRecorderSessionController(),
            )

        val state = viewModel.uiState.value
        assertEquals("Waiting for right pedal", state.setup.overallStatus)
        assertEquals("Pair Right Pedal", state.setup.primaryActionLabel)
        assertTrue(state.setup.primaryActionEnabled)
        assertNull(state.setup.debugActionLabel)
        assertEquals("Left Assioma", state.setup.devices.first().statusLabel)
        assertFalse(state.setup.canStartRide)
    }

    @Test
    fun initShowsDebugDemoSensorsActionWhenEnabledWithoutRememberedDevices() {
        val viewModel =
            AppViewModel(
                rememberedDeviceStore = FakeRememberedDeviceStore(),
                rideStore = FakeRideStore(),
                recorderSessionController = FakeRecorderSessionController(),
                allowDebugDemoSensors = true,
            )

        val state = viewModel.uiState.value
        assertEquals("Use Demo Sensors", state.setup.debugActionLabel)
        assertTrue(state.setup.debugActionEnabled)
        assertFalse(state.setup.canStartRide)
    }

    @Test
    fun associationRequestedMarksPendingRoleAsSearching() {
        val viewModel = AppViewModel(FakeRememberedDeviceStore(), FakeRideStore(), FakeRecorderSessionController())

        viewModel.onSetupAssociationRequested(SetupDeviceRole.LEFT_PEDAL)

        val state = viewModel.uiState.value
        assertEquals("Searching for left pedal", state.setup.overallStatus)
        assertEquals("Searching for left pedal", state.setup.primaryActionLabel)
        assertFalse(state.setup.primaryActionEnabled)
        assertFalse(state.setup.secondaryActionEnabled)
        assertEquals("Searching", state.setup.devices.first().statusLabel)
        assertTrue(viewModel.isAssociationPending())
    }

    @Test
    fun associationSucceededPersistsAndAdvancesToNextMissingRole() {
        val rememberedDeviceStore = FakeRememberedDeviceStore()
        val viewModel = AppViewModel(rememberedDeviceStore, FakeRideStore(), FakeRecorderSessionController())

        viewModel.onSetupAssociationRequested(SetupDeviceRole.LEFT_PEDAL)
        viewModel.onSetupAssociationSucceeded(
            SetupDeviceRole.LEFT_PEDAL,
            rememberedDevice(1, "Left Assioma", "left-id"),
        )

        val state = viewModel.uiState.value
        assertEquals("Waiting for right pedal", state.setup.overallStatus)
        assertEquals("Pair Right Pedal", state.setup.primaryActionLabel)
        assertTrue(state.setup.primaryActionEnabled)
        assertEquals("Left Assioma", state.setup.devices.first().statusLabel)
        assertEquals("left-id", rememberedDeviceStore.loadRememberedDevices().leftPedal?.association?.deviceId)
        assertFalse(viewModel.isAssociationPending())
    }

    @Test
    fun setupSecondaryActionClearsRememberedDevices() {
        val viewModel =
            AppViewModel(
                rememberedDeviceStore =
                    FakeRememberedDeviceStore(
                        initialDevices =
                            RememberedDevices(
                                leftPedal = rememberedDevice(1, "Left Assioma", "left-id"),
                                rightPedal = rememberedDevice(2, "Right Assioma", "right-id"),
                                heartRate = rememberedDevice(3, "HR Strap", "hr-id"),
                            ),
                    ),
                rideStore = FakeRideStore(),
                recorderSessionController = FakeRecorderSessionController(),
            )

        viewModel.onSetupSecondaryAction()

        val state = viewModel.uiState.value
        assertEquals("Waiting for left pedal", state.setup.overallStatus)
        assertEquals("Pair Left Pedal", state.setup.primaryActionLabel)
        assertTrue(state.setup.primaryActionEnabled)
        assertFalse(state.setup.canStartRide)
        assertFalse(viewModel.rememberedDevices().hasAnyRememberedDevice())
    }

    @Test
    fun setupPrimaryActionShowsPendingLiveStateWhenRideCanStart() =
        runBlocking {
            val viewModel =
                AppViewModel(
                    rememberedDeviceStore =
                        FakeRememberedDeviceStore(
                            initialDevices =
                                RememberedDevices(
                                    leftPedal = rememberedDevice(1, "Left Assioma", "left-id"),
                                    rightPedal = rememberedDevice(2, "Right Assioma", "right-id"),
                                    heartRate = rememberedDevice(3, "HR Strap", "hr-id"),
                                ),
                        ),
                    rideStore = FakeRideStore(),
                    recorderSessionController = FakeRecorderSessionController(),
                )

            viewModel.onSetupPrimaryAction()

            val live = viewModel.uiState.value.live
            assertEquals(AppScreen.LIVE, viewModel.uiState.value.currentScreen)
            assertEquals("00:00", live.elapsedLabel)
            assertEquals(0, live.powerWatts)
            assertEquals("Starting", live.zoneLabel)
        }

    @Test
    fun setupDebugActionShowsPendingLiveStateWhenDebugDemoSensorsAreAvailable() =
        runBlocking {
            val viewModel =
                AppViewModel(
                    rememberedDeviceStore = FakeRememberedDeviceStore(),
                    rideStore = FakeRideStore(),
                    recorderSessionController = FakeRecorderSessionController(),
                    allowDebugDemoSensors = true,
                )

            viewModel.onSetupDebugAction()

            val live = viewModel.uiState.value.live
            assertEquals(AppScreen.LIVE, viewModel.uiState.value.currentScreen)
            assertEquals("00:00", live.elapsedLabel)
            assertEquals(0, live.powerWatts)
            assertEquals("Starting", live.zoneLabel)
        }

    @Test
    fun activeRecorderStateRefreshesLiveRideMetrics() {
        val recorderController = FakeRecorderSessionController()
        val viewModel = AppViewModel(FakeRememberedDeviceStore(), FakeRideStore(), recorderController)

        recorderController.emit(
            RecorderSessionState.Active(
                rideId = "ride-1",
                liveFrame =
                    RecorderLiveFrame(
                        elapsedLabel = "00:12",
                        powerWatts = 214,
                        cadenceRpm = 88,
                        heartRateBpm = 148,
                        zoneLabel = "Zone 3",
                        zoneProgress = 0.48f,
                        truthStrip = null,
                        secondaryActionLabel = "Simulate Pedal Dropout",
                    ),
            ),
        )
        flushMainThread()

        val live = viewModel.uiState.value.live
        assertEquals(AppScreen.LIVE, viewModel.uiState.value.currentScreen)
        assertEquals("00:12", live.elapsedLabel)
        assertEquals(214, live.powerWatts)
        assertEquals(88, live.cadenceRpm)
        assertEquals(148, live.heartRateBpm)
        assertEquals("Simulate Pedal Dropout", live.secondaryActionLabel)
    }

    @Test
    fun completedRecorderStateLoadsSummaryFromStoredRideData() =
        runBlocking {
            val rideStore = FakeRideStore()
            val recorderController = FakeRecorderSessionController()
            val viewModel = AppViewModel(FakeRememberedDeviceStore(), rideStore, recorderController)
            val rideId = "ride-1"
            val samples = PreviewRideData.demoRideSamples()

            rideStore.startSession(PreviewRideData.demoRideSession(rideId))
            rideStore.appendSamples(rideId, samples)
            rideStore.finishSession(rideId, samples.last().timestampEpochSeconds)
            rideStore.saveSummary(rideId, RideSummaryCalculator.calculate(samples))

            recorderController.emit(RecorderSessionState.Completed(rideId))
            flushMainThread()

            val state = viewModel.uiState.value
            assertEquals(AppScreen.SUMMARY, state.currentScreen)
            assertEquals(rideId, state.summary.rideId)
            assertEquals("02:40 indoor ride", state.summary.rideLabel)
            assertEquals("100 W", state.summary.averagePowerLabel)
            assertEquals("90 rpm", state.summary.averageCadenceLabel)
            assertEquals("147 bpm", state.summary.averageHeartRateLabel)
            assertEquals("Export FIT", state.summary.exportLabel)
        }

    @Test
    fun completedRecorderStateKeepsDropoutSummaryTruthful() =
        runBlocking {
            val rideStore = FakeRideStore()
            val recorderController = FakeRecorderSessionController()
            val viewModel = AppViewModel(FakeRememberedDeviceStore(), rideStore, recorderController)
            val rideId = "ride-1"
            val samples = PreviewRideData.demoRideSamples(includePedalDropout = true)

            rideStore.startSession(PreviewRideData.demoRideSession(rideId))
            rideStore.appendSamples(rideId, samples)
            rideStore.finishSession(rideId, samples.last().timestampEpochSeconds)
            rideStore.saveSummary(rideId, RideSummaryCalculator.calculate(samples))

            recorderController.emit(RecorderSessionState.Completed(rideId))
            flushMainThread()

            val summary = viewModel.uiState.value.summary
            assertEquals(2, summary.asymmetryIntervals.size)
            assertTrue(summary.asymmetryMessage.contains("limited"))
            assertEquals("91 W", summary.averagePowerLabel)
        }

    @Test
    fun summaryResetReturnsToSetupWithoutLosingRememberedDevices() {
        val viewModel =
            AppViewModel(
                rememberedDeviceStore =
                    FakeRememberedDeviceStore(
                        initialDevices =
                            RememberedDevices(
                                leftPedal = rememberedDevice(1, "Left Assioma", "left-id"),
                                rightPedal = rememberedDevice(2, "Right Assioma", "right-id"),
                                heartRate = rememberedDevice(3, "HR Strap", "hr-id"),
                            ),
                    ),
                rideStore = FakeRideStore(),
                recorderSessionController = FakeRecorderSessionController(),
            )

        viewModel.onSummaryReset()

        val state = viewModel.uiState.value
        assertEquals(AppScreen.SETUP, state.currentScreen)
        assertEquals("All sensors ready", state.setup.overallStatus)
        assertEquals("Start Demo Ride", state.setup.primaryActionLabel)
        assertTrue(state.setup.primaryActionEnabled)
        assertEquals("Export FIT", state.summary.exportLabel)
        assertEquals("Start Another Ride", state.summary.resetLabel)
    }

    @Test
    fun staleAssociationCallbackIsIgnoredAfterReset() {
        val rememberedDeviceStore = FakeRememberedDeviceStore()
        val viewModel = AppViewModel(rememberedDeviceStore, FakeRideStore(), FakeRecorderSessionController())

        viewModel.onSetupAssociationRequested(SetupDeviceRole.LEFT_PEDAL)
        viewModel.onSetupSecondaryAction()

        // Simulate stale callback arriving after the user already cleared the pending pairing
        viewModel.onSetupAssociationSucceeded(
            SetupDeviceRole.LEFT_PEDAL,
            rememberedDevice(1, "Left Assioma", "left-id"),
        )

        assertNull(rememberedDeviceStore.loadRememberedDevices().leftPedal)
        assertFalse(viewModel.isAssociationPending())
    }

    @Test
    fun summaryExportStateChangeUpdatesUiAndStoredSummary() =
        runBlocking {
            val rideStore = FakeRideStore()
            val recorderController = FakeRecorderSessionController()
            val viewModel = AppViewModel(FakeRememberedDeviceStore(), rideStore, recorderController)
            val rideId = "ride-1"
            val samples = PreviewRideData.demoRideSamples()

            rideStore.startSession(PreviewRideData.demoRideSession(rideId))
            rideStore.appendSamples(rideId, samples)
            rideStore.finishSession(rideId, samples.last().timestampEpochSeconds)
            rideStore.saveSummary(rideId, RideSummaryCalculator.calculate(samples))

            recorderController.emit(RecorderSessionState.Completed(rideId))
            flushMainThread()

            viewModel.onSummaryExportStateChanged(rideId, SyncState.EXPORTED)

            assertEquals("Export FIT Again", viewModel.uiState.value.summary.exportLabel)
            assertTrue(viewModel.uiState.value.summary.exportStatusMessage.contains("generated"))
            assertEquals(SyncState.EXPORTED, rideStore.loadSummary(rideId)?.exportState)
        }

    private fun flushMainThread() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun rememberedDevice(
        associationId: Int,
        displayName: String,
        deviceId: String,
    ) = RememberedDevice(associationId, DeviceAssociation(deviceId = deviceId, displayName = displayName))
}
