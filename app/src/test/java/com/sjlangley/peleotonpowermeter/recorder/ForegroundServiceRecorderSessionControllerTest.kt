package com.sjlangley.peleotonpowermeter.recorder

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForegroundServiceRecorderSessionControllerTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val sessionStateStore = RecorderSessionStateStore()
    private val controller = ForegroundServiceRecorderSessionController(application, sessionStateStore)

    @Test
    fun startDemoRideStartsForegroundRecorderServiceWithStartAction() =
        runBlocking {
            controller.startDemoRide()

            val startedIntent = shadowOf(application).nextStartedService
            assertEquals(RideRecorderService::class.java.name, startedIntent.component?.className)
            assertEquals(RideRecorderService.ACTION_START_DEMO_RIDE, startedIntent.action)
        }

    @Test
    fun togglePedalDropoutStartsForegroundRecorderServiceWithToggleAction() =
        runBlocking {
            controller.togglePedalDropout()

            val startedIntent = shadowOf(application).nextStartedService
            assertEquals(RideRecorderService::class.java.name, startedIntent.component?.className)
            assertEquals(RideRecorderService.ACTION_TOGGLE_PEDAL_DROPOUT, startedIntent.action)
        }

    @Test
    fun finishRideStartsForegroundRecorderServiceWithFinishAction() =
        runBlocking {
            controller.finishRide()

            val startedIntent = shadowOf(application).nextStartedService
            assertEquals(RideRecorderService::class.java.name, startedIntent.component?.className)
            assertEquals(RideRecorderService.ACTION_FINISH_RIDE, startedIntent.action)
        }

    @Test
    fun sessionStateReflectsSharedStoreUpdates() {
        val state = RecorderSessionState.Completed("ride-1")

        sessionStateStore.publish(state)

        assertEquals(state, controller.sessionState.value)
    }

    @Test
    fun resetClearsCompletedStateFromSharedStore() {
        sessionStateStore.publish(RecorderSessionState.Completed("ride-1"))

        controller.reset()

        assertEquals(RecorderSessionState.Idle, controller.sessionState.value)
    }
}
