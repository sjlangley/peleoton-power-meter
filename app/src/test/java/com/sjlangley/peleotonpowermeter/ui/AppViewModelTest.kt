package com.sjlangley.peleotonpowermeter.ui

import com.sjlangley.peleotonpowermeter.data.model.AppScreen
import com.sjlangley.peleotonpowermeter.testutil.FakeRideStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppViewModelTest {
    @Test
    fun setupSecondaryActionTogglesHeartRateReadiness() {
        val viewModel = AppViewModel(FakeRideStore())

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
            val viewModel = AppViewModel(FakeRideStore())

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
    fun startingRidePersistsSessionAndInitialSamples() =
        runBlocking {
            val rideStore = FakeRideStore()
            val viewModel = AppViewModel(rideStore)

            viewModel.onSetupPrimaryAction()

            val rideId = rideStore.sessions.keys.single()
            assertNotNull(rideStore.sessions[rideId])
            assertEquals(12, rideStore.samples[rideId]?.size)
            assertEquals(AppScreen.LIVE, viewModel.uiState.value.currentScreen)
        }

    @Test
    fun finishingRideBuildsSummaryFromPersistedSamples() =
        runBlocking {
            val rideStore = FakeRideStore()
            val viewModel = AppViewModel(rideStore)

            viewModel.onSetupPrimaryAction()
            viewModel.onLivePrimaryAction()

            val state = viewModel.uiState.value
            val rideId = rideStore.sessions.keys.single()

            assertEquals(AppScreen.SUMMARY, state.currentScreen)
            assertEquals("02:40 indoor ride", state.summary.rideLabel)
            assertEquals("100 W", state.summary.averagePowerLabel)
            assertEquals("90 rpm", state.summary.averageCadenceLabel)
            assertEquals("147 bpm", state.summary.averageHeartRateLabel)
            assertEquals(3, state.summary.asymmetryIntervals.size)
            assertEquals("02:10", state.summary.asymmetryIntervals.first().startLabel)
            assertEquals(160, rideStore.samples[rideId]?.size)
            assertNotNull(rideStore.summaries[rideId])
        }

    @Test
    fun liveSecondaryActionRestoresSensorsAfterDropout() =
        runBlocking {
            val viewModel = AppViewModel(FakeRideStore())

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
    fun finishingRideAfterDropoutKeepsSummaryTruthful() =
        runBlocking {
            val rideStore = FakeRideStore()
            val viewModel = AppViewModel(rideStore)

            viewModel.onSetupPrimaryAction()
            viewModel.onLiveSecondaryAction()
            viewModel.onLivePrimaryAction()

            val summary = viewModel.uiState.value.summary
            val rideId = rideStore.sessions.keys.single()

            assertEquals(2, summary.asymmetryIntervals.size)
            assertTrue(summary.asymmetryMessage.contains("limited"))
            assertEquals("91 W", summary.averagePowerLabel)
            assertEquals(160, rideStore.samples[rideId]?.size)
        }

    @Test
    fun summaryResetRestoresInitialAppState() =
        runBlocking {
            val viewModel = AppViewModel(FakeRideStore())

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

    @Test
    fun startingAnotherRideUsesANewRideId() =
        runBlocking {
            val rideStore = FakeRideStore()
            val viewModel = AppViewModel(rideStore)

            viewModel.onSetupPrimaryAction()
            val firstRideId = rideStore.sessions.keys.single()
            viewModel.onLivePrimaryAction()
            viewModel.onSummaryReset()

            viewModel.onSetupPrimaryAction()
            val secondRideId = rideStore.sessions.keys.single { it != firstRideId }

            assertNotEquals(firstRideId, secondRideId)
        }

    @Test
    fun repeatedSetupPrimaryActionDoesNotStartAnotherRideFromLiveScreen() =
        runBlocking {
            val rideStore = FakeRideStore()
            val viewModel = AppViewModel(rideStore)

            viewModel.onSetupPrimaryAction()
            val firstRideId = rideStore.sessions.keys.single()

            viewModel.onSetupPrimaryAction()

            assertEquals(listOf(firstRideId), rideStore.sessions.keys.toList())
            assertEquals(AppScreen.LIVE, viewModel.uiState.value.currentScreen)
        }
}
