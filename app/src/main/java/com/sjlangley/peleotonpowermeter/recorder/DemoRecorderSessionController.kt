package com.sjlangley.peleotonpowermeter.recorder

import com.sjlangley.peleotonpowermeter.data.model.PreviewRideData
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.domain.RideSummaryCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class DemoRecorderSessionController(
    private val rideStore: RideStore,
    private val tickDelayMillis: Long = DEFAULT_TICK_DELAY_MILLIS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : RecorderSessionController {
    private val sessionMutex = Mutex()
    private val _sessionState = MutableStateFlow<RecorderSessionState>(RecorderSessionState.Idle)

    override val sessionState: StateFlow<RecorderSessionState> = _sessionState.asStateFlow()

    private var recordingJob: Job? = null
    private var activeRideId: String? = null
    private var nextSampleIndex = 0
    private var pedalDropoutEnabled = false

    override suspend fun startDemoRide() {
        sessionMutex.withLock {
            if (_sessionState.value is RecorderSessionState.Active) {
                return
            }

            val rideId = nextRideId()
            activeRideId = rideId
            nextSampleIndex = 0
            pedalDropoutEnabled = false
            rideStore.startSession(PreviewRideData.demoRideSession(rideId))

            recordingJob?.cancel()
            recordingJob =
                scope.launch {
                    runLoop(rideId)
                }
        }
    }

    override suspend fun togglePedalDropout() {
        sessionMutex.withLock {
            val rideId = activeRideId ?: return
            pedalDropoutEnabled = !pedalDropoutEnabled

            val lastPersistedSample = rideStore.loadSamples(rideId).lastOrNull()
            if (lastPersistedSample != null) {
                _sessionState.value =
                    RecorderSessionState.Active(
                        rideId = rideId,
                        liveFrame = lastPersistedSample.toLiveFrame(pedalDropoutEnabled),
                    )
            }
        }
    }

    override suspend fun finishRide() {
        val rideId: String
        val remainingSamples: List<RideSample>

        sessionMutex.withLock {
            rideId = activeRideId ?: return
            recordingJob = null

            remainingSamples =
                (nextSampleIndex until TOTAL_SAMPLE_COUNT).map { index ->
                    sampleAt(index)
                }

            rideStore.appendSamples(rideId, remainingSamples)
            finalizeRideLocked(rideId)
        }
    }

    override fun reset() {
        if (_sessionState.value is RecorderSessionState.Completed) {
            _sessionState.value = RecorderSessionState.Idle
        }
    }

    fun cancel() {
        recordingJob?.cancel()
        scope.cancel()
    }

    private suspend fun runLoop(rideId: String) {
        while (true) {
            val shouldContinue =
                sessionMutex.withLock {
                    if (activeRideId != rideId || nextSampleIndex >= TOTAL_SAMPLE_COUNT) {
                        return
                    }

                    val sample = sampleAt(nextSampleIndex)
                    rideStore.appendSample(rideId, sample)
                    nextSampleIndex += 1

                    if (nextSampleIndex >= TOTAL_SAMPLE_COUNT) {
                        finalizeRideLocked(rideId)
                        false
                    } else {
                        _sessionState.value =
                            RecorderSessionState.Active(
                                rideId = rideId,
                                liveFrame = sample.toLiveFrame(pedalDropoutEnabled),
                            )
                        true
                    }
                }

            if (!shouldContinue) {
                return
            }

            delay(tickDelayMillis)
        }
    }

    private suspend fun finalizeRideLocked(rideId: String) {
        val storedSamples = rideStore.loadSamples(rideId)
        if (storedSamples.isEmpty()) {
            activeRideId = null
            nextSampleIndex = 0
            pedalDropoutEnabled = false
            _sessionState.value = RecorderSessionState.Idle
            return
        }

        rideStore.finishSession(rideId, storedSamples.last().timestampEpochSeconds)
        rideStore.saveSummary(
            rideId,
            RideSummaryCalculator.calculate(storedSamples),
        )

        activeRideId = null
        nextSampleIndex = 0
        pedalDropoutEnabled = false
        _sessionState.value = RecorderSessionState.Completed(rideId)
    }

    private fun sampleAt(index: Int): RideSample =
        PreviewRideData.demoRideSamples(includePedalDropout = pedalDropoutEnabled)[index]

    private fun nextRideId(): String = "demo-ride-${UUID.randomUUID()}"

    private fun RideSample.toLiveFrame(pedalDropoutEnabled: Boolean): RecorderLiveFrame =
        RecorderLiveFrame(
            elapsedLabel = timestampEpochSeconds.asElapsedLabel(),
            powerWatts = totalPowerWatts,
            cadenceRpm = cadenceRpm,
            heartRateBpm = heartRateBpm,
            zoneLabel = if (pedalDropoutEnabled) "Zone 4" else "Zone 3",
            zoneProgress = if (pedalDropoutEnabled) 0.62f else 0.48f,
            truthStrip =
                if (pedalDropoutEnabled) {
                    "Left pedal disconnected. Recording continues. Balance is partial."
                } else {
                    null
                },
            secondaryActionLabel = if (pedalDropoutEnabled) "Restore Sensors" else "Simulate Pedal Dropout",
        )

    private fun Long.asElapsedLabel(): String {
        val minutes = this / 60
        val seconds = this % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        const val DEFAULT_TICK_DELAY_MILLIS = 250L
        private val TOTAL_SAMPLE_COUNT = PreviewRideData.demoRideSamples().size
    }
}
