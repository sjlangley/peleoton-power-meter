package com.sjlangley.peleotonpowermeter.data.repo

import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.RideSession

interface RideStore {
    suspend fun startSession(session: RideSession)

    // Persisted samples are the boring 1 Hz contract for v1. Higher-frequency
    // sensor updates can exist in memory, but they should be normalized before
    // hitting storage.
    suspend fun appendSample(rideId: String, sample: RideSample)

    suspend fun finishSession(rideId: String, endedAtEpochSeconds: Long)
    suspend fun saveSummary(rideId: String, summary: DerivedSummary)
    suspend fun loadSession(rideId: String): RideSession?
    suspend fun loadSamples(rideId: String): List<RideSample>
    suspend fun loadSummary(rideId: String): DerivedSummary?
}
