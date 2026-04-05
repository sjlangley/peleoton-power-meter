package com.sjlangley.peleotonpowermeter.recorder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.RideSession
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for BleRecorderSessionController.
 *
 * Note: Full BLE integration testing requires characteristic notification support,
 * which is pending future work. These tests focus on session lifecycle and state management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BleRecorderSessionControllerTest {
    private lateinit var context: Context
    private lateinit var controller: BleRecorderSessionController
    private lateinit var rideStore: FakeRideStore
    private lateinit var testScope: CoroutineScope
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        rideStore = FakeRideStore()
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)

        controller = BleRecorderSessionController(
            context = context,
            rideStore = rideStore,
            leftPedalAddress = "AA:BB:CC:DD:EE:01",
            rightPedalAddress = "AA:BB:CC:DD:EE:02",
            heartRateAddress = "AA:BB:CC:DD:EE:03",
            ftpWatts = 200,
            tickDelayMillis = 100, // Fast ticks for testing
            scope = testScope,
        )
    }

    @After
    fun tearDown() {
        controller.cancel()
        testScope.cancel()
    }

    @Test
    fun initialState_isIdle() {
        assertEquals(RecorderSessionState.Idle, controller.sessionState.value)
    }

    @Test
    fun startDemoRide_throwsUnsupportedOperationException() = runTest {
        try {
            controller.startDemoRide()
            throw AssertionError("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("BleRecorderSessionController does not support demo rides") == true)
        }
    }

    @Test
    fun startRide_createsSessionInStore() = runTest {
        controller.startRide()
        testDispatcher.scheduler.advanceTimeBy(50) // Allow session creation

        assertEquals(1, rideStore.sessions.size)
        val session = rideStore.sessions.values.first()
        assertTrue(session.rideId.startsWith("ble-ride-"))
        assertEquals(200, session.ftpWatts)
    }

    @Test
    fun startRide_transitionsToActiveState() = runTest {
        controller.startRide()
        testDispatcher.scheduler.advanceTimeBy(50)

        val state = controller.sessionState.value
        assertTrue(state is RecorderSessionState.Active)
        assertTrue((state as RecorderSessionState.Active).rideId.startsWith("ble-ride-"))
    }

    @Test
    fun startRide_whileAlreadyActive_doesNothing() = runTest {
        controller.startRide()
        testDispatcher.scheduler.advanceTimeBy(50)

        val firstState = controller.sessionState.value as RecorderSessionState.Active
        val firstRideId = firstState.rideId

        controller.startRide()
        testDispatcher.scheduler.advanceTimeBy(50)

        val secondState = controller.sessionState.value as RecorderSessionState.Active
        assertEquals(firstRideId, secondState.rideId)
        assertEquals(1, rideStore.sessions.size)
    }

    @Test
    fun finishRide_transitionsToCompleted() = runTest {
        controller.startRide()
        testDispatcher.scheduler.advanceTimeBy(300) // Allow some samples to be generated

        controller.finishRide()
        testDispatcher.scheduler.advanceTimeBy(50)

        val state = controller.sessionState.value
        assertTrue(state is RecorderSessionState.Completed)
        assertTrue((state as RecorderSessionState.Completed).rideId.startsWith("ble-ride-"))
    }

    @Test
    fun finishRide_finalizesSessionInStore() = runTest {
        controller.startRide()
        testDispatcher.scheduler.advanceTimeBy(300)

        val activeRideId = (controller.sessionState.value as RecorderSessionState.Active).rideId

        controller.finishRide()
        testDispatcher.scheduler.advanceTimeBy(50)

        val session = rideStore.sessions[activeRideId]
        assertTrue(session?.endedAtEpochSeconds != null)
        assertTrue(rideStore.summaries.containsKey(activeRideId))
    }

    @Test
    fun reset_fromCompleted_transitionsToIdle() = runTest {
        controller.startRide()
        testDispatcher.scheduler.advanceTimeBy(200)

        controller.finishRide()
        testDispatcher.scheduler.advanceTimeBy(50)

        controller.reset()

        assertEquals(RecorderSessionState.Idle, controller.sessionState.value)
    }

    @Test
    fun togglePedalDropout_doesNothing() = runTest {
        // This is a no-op for BLE controller (demo-specific functionality)
        controller.startRide()
        testDispatcher.scheduler.advanceTimeBy(100)

        controller.togglePedalDropout()
        testDispatcher.scheduler.advanceTimeBy(100)

        // Should still be in Active state with no errors
        assertTrue(controller.sessionState.value is RecorderSessionState.Active)
    }

    /**
     * Fake RideStore implementation for testing.
     */
    private class FakeRideStore : RideStore {
        val sessions = mutableMapOf<String, RideSession>()
        val samples = mutableMapOf<String, MutableList<RideSample>>()
        val summaries = mutableMapOf<String, DerivedSummary>()

        override suspend fun startSession(session: RideSession) {
            sessions[session.rideId] = session
            samples[session.rideId] = mutableListOf()
        }

        override suspend fun appendSample(rideId: String, sample: RideSample) {
            samples.getOrPut(rideId) { mutableListOf() }.add(sample)
        }

        override suspend fun appendSamples(rideId: String, samples: List<RideSample>) {
            this.samples.getOrPut(rideId) { mutableListOf() }.addAll(samples)
        }

        override suspend fun finishSession(rideId: String, endedAtEpochSeconds: Long) {
            val session = sessions[rideId] ?: return
            sessions[rideId] = session.copy(endedAtEpochSeconds = endedAtEpochSeconds)
        }

        override suspend fun saveSummary(rideId: String, summary: DerivedSummary) {
            summaries[rideId] = summary
        }

        override suspend fun loadSession(rideId: String): RideSession? = sessions[rideId]

        override suspend fun loadSamples(rideId: String): List<RideSample> =
            samples[rideId] ?: emptyList()

        override suspend fun loadSummary(rideId: String): DerivedSummary? = summaries[rideId]
    }
}
