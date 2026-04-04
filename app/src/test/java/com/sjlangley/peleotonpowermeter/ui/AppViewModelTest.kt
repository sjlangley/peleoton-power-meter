package com.sjlangley.peleotonpowermeter.ui

import android.os.Looper
import com.sjlangley.peleotonpowermeter.data.model.AppScreen
import com.sjlangley.peleotonpowermeter.data.model.PreviewRideData
import com.sjlangley.peleotonpowermeter.domain.RideSummaryCalculator
import com.sjlangley.peleotonpowermeter.recorder.RecorderLiveFrame
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionState
import com.sjlangley.peleotonpowermeter.testutil.FakeRecorderSessionController
import com.sjlangley.peleotonpowermeter.testutil.FakeRideStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun setupSecondaryActionTogglesHeartRateReadiness() {
        val viewModel = AppViewModel(FakeRideStore(), FakeRecorderSessionController())

        viewModel.onSetupSecondaryAction()

        var state = viewModel.uiState.value
        assertFalse(state.setup.canStartRide)
        assertEquals("Waiting for heart-rate monitor", state.setup.overallStatus)
        assertEquals("Continue Pairing", state.setup.primaryActionLabel)
        assertEquals("Restore HR", state.setup.secondaryActionLabel)

        viewModel.onSetupSecondaryAction()

        state = viewModel.uiState.value
        assertTrue(state.setup.canStartRide)
        assertEquals("All sensors ready", state.setup.overallStatus)
        assertEquals("Start Demo Ride", state.setup.primaryActionLabel)
        assertEquals("Simulate Missing HR", state.setup.secondaryActionLabel)
    }

    @Test
    fun setupPrimaryActionReconnectsHeartRateWhenRideCannotStart() =
        runBlocking {
            val viewModel = AppViewModel(FakeRideStore(), FakeRecorderSessionController())

            viewModel.onSetupSecondaryAction()
            viewModel.onSetupPrimaryAction()

            val state = viewModel.uiState.value

            assertTrue(state.setup.canStartRide)
            assertEquals("All sensors ready", state.setup.overallStatus)
            assertEquals("Start Demo Ride", state.setup.primaryActionLabel)
            assertEquals("Simulate Missing HR", state.setup.secondaryActionLabel)
            assertEquals("Connected", state.setup.devices.last().statusLabel)
        }

    @Test
    fun setupPrimaryActionShowsPendingLiveStateWhenRideCanStart() =
        runBlocking {
            val viewModel = AppViewModel(FakeRideStore(), FakeRecorderSessionController())

            viewModel.onSetupPrimaryAction()

            val live = viewModel.uiState.value.live
            assertEquals(AppScreen.LIVE, viewModel.uiState.value.currentScreen)
            assertEquals("00:00", live.elapsedLabel)
            assertEquals(0, live.powerWatts)
            assertEquals("Starting", live.zoneLabel)
        }

    @Test
    fun activeRecorderStateRefreshesLiveRideMetrics() {
        val recorderController = FakeRecorderSessionController()
        val viewModel = AppViewModel(FakeRideStore(), recorderController)

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
            val viewModel = AppViewModel(rideStore, recorderController)
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
            assertEquals("02:40 indoor ride", state.summary.rideLabel)
            assertEquals("100 W", state.summary.averagePowerLabel)
            assertEquals("90 rpm", state.summary.averageCadenceLabel)
            assertEquals("147 bpm", state.summary.averageHeartRateLabel)
        }

    @Test
    fun completedRecorderStateKeepsDropoutSummaryTruthful() =
        runBlocking {
            val rideStore = FakeRideStore()
            val recorderController = FakeRecorderSessionController()
            val viewModel = AppViewModel(rideStore, recorderController)
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
    fun summaryResetRestoresInitialAppState() {
        val viewModel = AppViewModel(FakeRideStore(), FakeRecorderSessionController())

        viewModel.onSummaryReset()

        val state = viewModel.uiState.value
        assertEquals(AppScreen.SETUP, state.currentScreen)
        assertEquals("All sensors ready", state.setup.overallStatus)
        assertEquals("Start Demo Ride", state.setup.primaryActionLabel)
        assertEquals("Share Demo Summary", state.summary.exportLabel)
    }

    private fun flushMainThread() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
