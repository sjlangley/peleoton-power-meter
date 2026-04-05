package com.sjlangley.peleotonpowermeter.recorder

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import com.sjlangley.peleotonpowermeter.ble.BleSampleCollector
import com.sjlangley.peleotonpowermeter.ble.BleConnectionManager
import com.sjlangley.peleotonpowermeter.ble.BleConnectionState
import com.sjlangley.peleotonpowermeter.ble.CyclingPowerParser
import com.sjlangley.peleotonpowermeter.ble.HeartRateParser
import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation
import com.sjlangley.peleotonpowermeter.data.model.HeartRateSource
import com.sjlangley.peleotonpowermeter.data.model.PedalPair
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.RideSession
import com.sjlangley.peleotonpowermeter.data.model.SyncState
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.domain.RideSummaryCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines. flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID

/**
 * BLE-based recorder session controller that connects to real power meters and heart rate monitors.
 *
 * This controller uses [BleConnectionManager] to connect to Assioma Duo pedals (left and right)
 * and a heart rate monitor, parses incoming characteristic notifications, normalizes data to
 * 1-second samples, and writes them to [RideStore].
 *
 * NOTE: This is an initial implementation that manages BLE connections directly. Full integration
 * with BleConnectionManager for characteristic notifications is pending future work.
 *
 * @param context Android context for BLE operations
 * @param rideStore Storage for ride sessions and samples
 * @param leftPedalAddress MAC address of left pedal (e.g., "AA:BB:CC:DD:EE:FF")
 * @param rightPedalAddress MAC address of right pedal
 * @param heartRateAddress MAC address of heart rate monitor
 * @param ftpWatts Functional Threshold Power in watts for zone calculation
 * @param tickDelayMillis Sample generation interval in milliseconds (default 1000ms = 1Hz)
 * @param scope Coroutine scope for controller operations
 */
@SuppressLint("MissingPermission")
class BleRecorderSessionController(
    private val context: Context,
    private val rideStore: RideStore,
    private val leftPedalAddress: String,
    private val rightPedalAddress: String,
    private val heartRateAddress: String,
    private val ftpWatts: Int = DEFAULT_FTP_WATTS,
    private val tickDelayMillis: Long = DEFAULT_TICK_DELAY_MILLIS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : RecorderSessionController {
    private val sessionMutex = Mutex()
    private val _sessionState = MutableStateFlow<RecorderSessionState>(RecorderSessionState.Idle)
    override val sessionState: StateFlow<RecorderSessionState> = _sessionState.asStateFlow()

    private var bleConnectionManager: BleConnectionManager? = null
    private var sampleCollector: BleSampleCollector? = null
    private var recordingJob: Job? = null
    private var activeRideId: String? = null
    private var rideStartTime: Long = 0L

    // TODO: Full BLE characteristic notification integration pending
    // For now, this controller sets up connections but requires additional
    // work to subscribe to characteristics and handle notifications

    override suspend fun startDemoRide() {
        // BLE controller doesn't support demo rides - this is for the demo controller only
        throw UnsupportedOperationException(
            "BleRecorderSessionController does not support demo rides. " +
                "Use DemoRecorderSessionController for demo functionality."
        )
    }

    /**
     * Start recording a real ride from connected BLE devices.
     *
     * Connects to all three devices (left pedal, right pedal, HR monitor),
     * subscribes to their characteristics, and begins collecting and normalizing
     * data to 1-second samples.
     */
    suspend fun startRide() {
        sessionMutex.withLock {
            if (_sessionState.value is RecorderSessionState.Active) {
                return
            }

            val rideId = nextRideId()
            activeRideId = rideId
            rideStartTime = System.currentTimeMillis() / 1000

            // Create BLE connection manager
            val manager = BleConnectionManager(context, scope)
            bleConnectionManager = manager

            // Create sample collector
            val collector = BleSampleCollector(ftpWatts)
            sampleCollector = collector

            // Start session in storage
            rideStore.startSession(
                RideSession(
                    rideId = rideId,
                    startedAtEpochSeconds = rideStartTime,
                    endedAtEpochSeconds = null,
                    ftpWatts = ftpWatts,
                    pedalPair = PedalPair(
                        left = DeviceAssociation(leftPedalAddress, "Left Assioma"),
                        right = DeviceAssociation(rightPedalAddress, "Right Assioma"),
                    ),
                    heartRateSource = HeartRateSource(
                        source = DeviceAssociation(heartRateAddress, "HR Monitor"),
                    ),
                    syncState = SyncState.LOCAL_ONLY,
                    interruptionDetected = false,
                )
            )

            // Connect to devices
            scope.launch {
                connectToDevices(manager, collector)
            }

            // Start sample generation loop
            recordingJob = scope.launch {
                runSampleLoop(rideId, collector)
            }
        }
    }

    private suspend fun connectToDevices(
        manager: BleConnectionManager,
        collector: BleSampleCollector,
    ) {
        // Connect to all three devices
        val leftFlow = manager.connect(leftPedalAddress)
        val rightFlow = manager.connect(rightPedalAddress)
        val hrFlow = manager.connect(heartRateAddress)

        // Monitor connection states and update collector
        scope.launch {
            leftFlow.collect { state ->
                collector.setLeftConnected(state is BleConnectionState.Connected)
            }
        }

        scope.launch {
            rightFlow.collect { state ->
                collector.setRightConnected(state is BleConnectionState.Connected)
            }
        }

        scope.launch {
            hrFlow.collect { state ->
                collector.setHeartRateConnected(state is BleConnectionState.Connected)
            }
        }

        // TODO: Subscribe to characteristics and parse notifications
        // This requires extending BleConnectionManager to support characteristic
        // notifications, or implementing characteristic subscription directly here.
        // For now, connections are established but data parsing is pending.
    }

    private suspend fun runSampleLoop(rideId: String, collector: BleSampleCollector) {
        var elapsedSeconds = 0L

        while (true) {
            sessionMutex.withLock {
                if (activeRideId != rideId) {
                    return
                }

                val timestampEpochSeconds = rideStartTime + elapsedSeconds
                val sample = collector.generateSample(timestampEpochSeconds)

                rideStore.appendSample(rideId, sample)
                elapsedSeconds += 1

                _sessionState.value = RecorderSessionState.Active(
                    rideId = rideId,
                    liveFrame = sample.toLiveFrame(),
                )
            }

            delay(tickDelayMillis)
        }
    }

    override suspend fun togglePedalDropout() {
        // Not applicable for real BLE recording - this is demo-specific functionality
        // In real scenarios, dropouts happen naturally and are reflected in connection states
    }

    override suspend fun finishRide() {
        sessionMutex.withLock {
            val rideId = activeRideId ?: return
            recordingJob?.cancel()
            recordingJob = null

            // Disconnect from all devices
            bleConnectionManager?.disconnectAll()
            bleConnectionManager = null

            // Finalize ride
            val storedSamples = rideStore.loadSamples(rideId)
            if (storedSamples.isNotEmpty()) {
                rideStore.finishSession(rideId, storedSamples.last().timestampEpochSeconds)
                rideStore.saveSummary(rideId, RideSummaryCalculator.calculate(storedSamples))
            }

            activeRideId = null
            sampleCollector = null
            _sessionState.value = RecorderSessionState.Completed(rideId)
        }
    }

    override fun reset() {
        if (_sessionState.value is RecorderSessionState.Completed) {
            _sessionState.value = RecorderSessionState.Idle
        }
    }

    fun cancel() {
        recordingJob?.cancel()
        scope.launch {
            bleConnectionManager?.disconnectAll()
        }
        scope.cancel()
    }

    private fun nextRideId(): String = "ble-ride-${UUID.randomUUID()}"

    private fun RideSample.toLiveFrame(): RecorderLiveFrame {
        val zoneLabels = arrayOf("Zone 1", "Zone 2", "Zone 3", "Zone 4", "Zone 5", "Zone 6", "Zone 7")
        val zoneLabel = zoneLabels.getOrNull(zoneIndex) ?: "Zone 1"

        // Calculate zone progress (0.0 to 1.0 within the zone)
        // This is a simplified calculation - real implementation would need more precise zone boundaries
        val zoneProgress = 0.5f // Simplified for now

        // Build truth strip for connection issues
        val truthStrip = buildString {
            val issues = mutableListOf<String>()
            if (!leftConnected) issues.add("Left pedal disconnected")
            if (!rightConnected) issues.add("Right pedal disconnected")
            if (!heartRateConnected) issues.add("HR monitor disconnected")

            if (issues.isNotEmpty()) {
                append(issues.joinToString(". "))
                append(". Recording continues.")
            }
        }.takeIf { it.isNotEmpty() }

        return RecorderLiveFrame(
            elapsedLabel = timestampEpochSeconds.asElapsedLabel(),
            powerWatts = totalPowerWatts,
            cadenceRpm = cadenceRpm,
            heartRateBpm = heartRateBpm,
            zoneLabel = zoneLabel,
            zoneProgress = zoneProgress,
            truthStrip = truthStrip,
            secondaryActionLabel = "Finish Ride",
        )
    }

    private fun Long.asElapsedLabel(): String {
        val hours = this / 3600
        val minutes = (this % 3600) / 60
        val seconds = this % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    companion object {
        private const val DEFAULT_TICK_DELAY_MILLIS = 1000L
        private const val DEFAULT_FTP_WATTS = 200

        // Bluetooth SIG UUIDs for power and heart rate services
        val CYCLING_POWER_SERVICE_UUID: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
        val CYCLING_POWER_MEASUREMENT_UUID: UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
