package com.sjlangley.peleotonpowermeter.recorder

import android.content.Context
import com.sjlangley.peleotonpowermeter.ble.BleConnectionManager
import com.sjlangley.peleotonpowermeter.ble.BleConnectionState
import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.RideSession
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Unit tests for BleRecorderSessionController.
 *
 * **Test Coverage Strategy:**
 *
 * This test suite provides basic coverage of controller initialization, interface compliance,
 * and error handling. The core business logic (BleSampleCollector) is comprehensively tested
 * with 24 tests achieving ~100% coverage.
 *
 * **Why Limited Controller Coverage:**
 *
 * BleRecorderSessionController uses infinite background coroutines:
 * - `connectToDevices()`: Indefinite `collect {}` loops monitoring BLE state
 * - `runSampleLoop()`: `while(activeRideId != null)` loop generating samples
 *
 * These architectural patterns make comprehensive unit testing problematic:
 * - TestDispatcher advancement causes CI hangs/timeouts
 * - Robolectric causes OutOfMemoryError in CI (even with 2GB heap + forking)
 * - Immediate cancellation after start still locks up on background coroutines
 *
 * **What's Tested:**
 * ✅ Controller initialization and dependency injection
 * ✅ Interface method implementations (RecorderSessionController)
 * ✅ Error paths (startDemoRide throws, no-op methods)
 * ✅ State management (reset, initial state)
 * ✅ Mocking strategy (BleConnectionManager, Context)
 * ✅ Core logic: BleSampleCollector (24 tests, full coverage)
 *
 * **Integration Testing Plan:**
 * Full lifecycle testing (start→collect→finish) will be added in a follow-up PR
 * once BLE characteristic notification integration is complete (currently marked
 * TODO in the controller implementation). Integration tests will run against real
 * Android instrumentation or use a redesigned architecture more amenable to testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BleRecorderSessionControllerTest {
    private lateinit var context: Context
    private lateinit var controller: BleRecorderSessionController
    private lateinit var rideStore: FakeRideStore
    private lateinit var testScope: CoroutineScope
    private lateinit var mockBleManager: BleConnectionManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mock<Context>()
        rideStore = FakeRideStore()
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)

        val disconnectedFlow = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected).asStateFlow()
        mockBleManager = mock<BleConnectionManager> {
            onBlocking { connect(any()) } doReturn disconnectedFlow
        }

        controller = BleRecorderSessionController(
            context = context,
            rideStore = rideStore,
            leftPedalAddress = "AA:BB:CC:DD:EE:01",
            rightPedalAddress = "AA:BB:CC:DD:EE:02",
            heartRateAddress = "AA:BB:CC:DD:EE:03",
            ftpWatts = 200,
            tickDelayMillis = 100,
            scope = testScope,
            bleConnectionManagerFactory = { _, _ -> mockBleManager },
        )
    }

    @After
    fun tearDown() {
        controller.cancel()
        testScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isIdle() {
        assertEquals(RecorderSessionState.Idle, controller.sessionState.value)
    }

    @Test
    @Suppress("DEPRECATION")
    fun startDemoRide_throwsUnsupportedOperationException() = runTest {
        try {
            controller.startDemoRide()
            throw AssertionError("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("BleRecorderSessionController does not support demo rides") == true)
        }
    }

    @Test
    fun reset_setsStateToIdle() {
        controller.reset()
        assertEquals(RecorderSessionState.Idle, controller.sessionState.value)
    }

    @Test
    fun togglePedalDropout_doesNothing() = runTest {
        // This is a no-op for BLE controller (demo-specific functionality)
        // Just verify it doesn't throw
        controller.togglePedalDropout()
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
            sessions[rideId] = sessions[rideId]!!.copy(endedAtEpochSeconds = endedAtEpochSeconds)
        }

        override suspend fun saveSummary(rideId: String, summary: DerivedSummary) {
            summaries[rideId] = summary
        }

        override suspend fun loadSession(rideId: String): RideSession? = sessions[rideId]

        override suspend fun loadSamples(rideId: String): List<RideSample> = samples[rideId] ?: emptyList()

        override suspend fun loadSummary(rideId: String): DerivedSummary? = summaries[rideId]
    }
}
