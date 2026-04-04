package com.sjlangley.peleotonpowermeter.recorder

import com.sjlangley.peleotonpowermeter.testutil.FakeRideStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoRecorderSessionControllerTest {
    @Test
    fun startDemoRidePersistsSamplesAndPublishesLiveState() =
        runBlocking {
            val rideStore = FakeRideStore()
            val controller = DemoRecorderSessionController(rideStore, tickDelayMillis = 1L)

            controller.startDemoRide()
            delay(10L)

            val activeState = controller.sessionState.value as RecorderSessionState.Active
            assertTrue(rideStore.samples.getValue(activeState.rideId).isNotEmpty())
            assertEquals("Simulate Pedal Dropout", activeState.liveFrame.secondaryActionLabel)
            assertTrue(activeState.liveFrame.powerWatts > 0)

            controller.cancel()
        }

    @Test
    fun togglePedalDropoutUpdatesTruthStrip() =
        runBlocking {
            val rideStore = FakeRideStore()
            val controller = DemoRecorderSessionController(rideStore, tickDelayMillis = 1L)

            controller.startDemoRide()
            delay(5L)
            controller.togglePedalDropout()

            val activeState = controller.sessionState.value as RecorderSessionState.Active
            assertNotNull(activeState.liveFrame.truthStrip)
            assertEquals("Restore Sensors", activeState.liveFrame.secondaryActionLabel)

            controller.cancel()
        }

    @Test
    fun finishRideBackfillsRemainingSamplesAndCompletesSummary() =
        runBlocking {
            val rideStore = FakeRideStore()
            val controller = DemoRecorderSessionController(rideStore, tickDelayMillis = 1L)

            controller.startDemoRide()
            delay(5L)

            val rideId = (controller.sessionState.value as RecorderSessionState.Active).rideId
            controller.finishRide()

            assertEquals(RecorderSessionState.Completed(rideId), controller.sessionState.value)
            assertEquals(160, rideStore.samples.getValue(rideId).size)
            assertNotNull(rideStore.summaries[rideId])

            controller.cancel()
        }

    @Test
    fun runLoopFinishesInCompletedStateAfterLastSample() =
        runBlocking {
            val rideStore = FakeRideStore()
            val controller = DemoRecorderSessionController(rideStore, tickDelayMillis = 1L)

            controller.startDemoRide()
            delay(250L)

            val completedState = controller.sessionState.value as RecorderSessionState.Completed
            assertEquals(160, rideStore.samples.getValue(completedState.rideId).size)
            assertNotNull(rideStore.summaries[completedState.rideId])

            controller.cancel()
        }
}
