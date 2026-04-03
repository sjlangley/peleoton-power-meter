package com.sjlangley.peleotonpowermeter.ui

import com.sjlangley.peleotonpowermeter.data.model.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppViewModelTest {
    @Test
    fun setupSecondaryActionTogglesHeartRateReadiness() {
        val viewModel = AppViewModel()

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
    fun setupPrimaryActionReconnectsHeartRateWhenRideCannotStart() {
        val viewModel = AppViewModel()

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
    fun finishingRideBuildsSummaryFromCalculatedSamples() {
        val viewModel = AppViewModel()

        viewModel.onSetupPrimaryAction()
        viewModel.onLivePrimaryAction()

        val state = viewModel.uiState.value

        assertEquals(AppScreen.SUMMARY, state.currentScreen)
        assertEquals("02:40 indoor ride", state.summary.rideLabel)
        assertEquals("100 W", state.summary.averagePowerLabel)
        assertEquals("90 rpm", state.summary.averageCadenceLabel)
        assertEquals("147 bpm", state.summary.averageHeartRateLabel)
        assertEquals(3, state.summary.asymmetryIntervals.size)
        assertEquals("02:10", state.summary.asymmetryIntervals.first().startLabel)
    }

    @Test
    fun liveSecondaryActionRestoresSensorsAfterDropout() {
        val viewModel = AppViewModel()

        viewModel.onSetupPrimaryAction()
        viewModel.onLiveSecondaryAction()
        viewModel.onLiveSecondaryAction()

        val live = viewModel.uiState.value.live

        assertEquals(null, live.truthStrip)
        assertEquals("Simulate Pedal Dropout", live.secondaryActionLabel)
        assertEquals(214, live.powerWatts)
        assertEquals(88, live.cadenceRpm)
        assertEquals(148, live.heartRateBpm)
    }

    @Test
    fun finishingRideAfterDropoutKeepsSummaryTruthful() {
        val viewModel = AppViewModel()

        viewModel.onSetupPrimaryAction()
        viewModel.onLiveSecondaryAction()
        viewModel.onLivePrimaryAction()

        val summary = viewModel.uiState.value.summary

        assertEquals(2, summary.asymmetryIntervals.size)
        assertTrue(summary.asymmetryMessage.contains("limited"))
        assertEquals("91 W", summary.averagePowerLabel)
    }

    @Test
    fun summaryResetRestoresInitialAppState() {
        val viewModel = AppViewModel()

        viewModel.onSetupPrimaryAction()
        viewModel.onLiveSecondaryAction()
        viewModel.onLivePrimaryAction()
        viewModel.onSummaryReset()

        val state = viewModel.uiState.value

        assertEquals(AppScreen.SETUP, state.currentScreen)
        assertEquals("All sensors ready", state.setup.overallStatus)
        assertEquals("Start Demo Ride", state.setup.primaryActionLabel)
        assertEquals("Share Demo Summary", state.summary.exportLabel)
    }
}
