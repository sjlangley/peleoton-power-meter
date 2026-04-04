package com.sjlangley.peleotonpowermeter.testutil

import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.RideSession
import com.sjlangley.peleotonpowermeter.data.repo.RideStore

class FakeRideStore : RideStore {
    val sessions = mutableMapOf<String, RideSession>()
    val samples = mutableMapOf<String, MutableList<RideSample>>()
    val summaries = mutableMapOf<String, DerivedSummary>()

    override suspend fun startSession(session: RideSession) {
        sessions[session.rideId] = session
    }

    override suspend fun appendSample(
        rideId: String,
        sample: RideSample,
    ) = appendSamples(rideId, listOf(sample))

    override suspend fun appendSamples(
        rideId: String,
        samples: List<RideSample>,
    ) {
        if (samples.isEmpty()) {
            return
        }

        this.samples.getOrPut(rideId) { mutableListOf() } += samples
    }

    override suspend fun finishSession(
        rideId: String,
        endedAtEpochSeconds: Long,
    ) {
        val session = checkNotNull(sessions[rideId])
        sessions[rideId] = session.copy(endedAtEpochSeconds = endedAtEpochSeconds)
    }

    override suspend fun saveSummary(
        rideId: String,
        summary: DerivedSummary,
    ) {
        summaries[rideId] = summary
    }

    override suspend fun loadSession(rideId: String): RideSession? = sessions[rideId]

    override suspend fun loadSamples(rideId: String): List<RideSample> = samples[rideId].orEmpty()

    override suspend fun loadSummary(rideId: String): DerivedSummary? = summaries[rideId]
}
