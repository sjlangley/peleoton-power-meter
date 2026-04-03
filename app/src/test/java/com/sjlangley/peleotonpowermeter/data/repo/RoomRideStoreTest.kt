package com.sjlangley.peleotonpowermeter.data.repo

import com.sjlangley.peleotonpowermeter.data.local.DerivedSummaryEntity
import com.sjlangley.peleotonpowermeter.data.local.RideDao
import com.sjlangley.peleotonpowermeter.data.local.RideSampleEntity
import com.sjlangley.peleotonpowermeter.data.local.RideSessionEntity
import com.sjlangley.peleotonpowermeter.data.model.AsymmetryInterval
import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation
import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.HeartRateSource
import com.sjlangley.peleotonpowermeter.data.model.PedalPair
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.RideSession
import com.sjlangley.peleotonpowermeter.data.model.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RoomRideStoreTest {
    @Test
    fun startSessionPersistsSessionEntity() =
        runBlocking {
            val rideDao = FakeRideDao()
            val store = RoomRideStore(rideDao)

            store.startSession(session())

            assertEquals("left-1", rideDao.sessions["ride-1"]?.leftDeviceId)
            assertEquals("right-1", rideDao.sessions["ride-1"]?.rightDeviceId)
            assertEquals("hr-1", rideDao.sessions["ride-1"]?.heartRateDeviceId)
        }

    @Test
    fun appendSamplePersistsOneSampleFrame() =
        runBlocking {
            val rideDao = FakeRideDao()
            val store = RoomRideStore(rideDao)

            store.appendSample("ride-1", sample())

            assertEquals(1, rideDao.samples.size)
            assertEquals("ride-1", rideDao.samples.single().rideId)
            assertEquals(210, rideDao.samples.single().totalPowerWatts)
        }

    @Test
    fun finishSessionUpdatesExistingEndTime() =
        runBlocking {
            val rideDao = FakeRideDao()
            val store = RoomRideStore(rideDao)
            store.startSession(session())

            store.finishSession("ride-1", endedAtEpochSeconds = 456L)

            assertEquals(456L, rideDao.sessions["ride-1"]?.endedAtEpochSeconds)
        }

    @Test
    fun finishSessionFailsWhenRideIsMissing() =
        runBlocking {
            val store = RoomRideStore(FakeRideDao())

            try {
                store.finishSession("missing", endedAtEpochSeconds = 1L)
                fail("Expected finishSession to fail for a missing ride.")
            } catch (_: IllegalStateException) {
                // Expected: finishing a ride before startSession is a caller bug.
            }
        }

    @Test
    fun saveSummaryPersistsFullDerivedSummary() =
        runBlocking {
            val rideDao = FakeRideDao()
            val store = RoomRideStore(rideDao)

            store.saveSummary("ride-1", summary())

            val saved = rideDao.summaries["ride-1"]
            assertEquals(mapOf(2 to 60, 3 to 40), saved?.timeInZoneSeconds)
            assertEquals(2, saved?.asymmetryIntervals?.size)
            assertEquals(true, saved?.partialBalance)
        }

    private fun session() =
        RideSession(
            rideId = "ride-1",
            startedAtEpochSeconds = 123L,
            endedAtEpochSeconds = null,
            ftpWatts = 250,
            pedalPair =
                PedalPair(
                    left = DeviceAssociation(deviceId = "left-1", displayName = "Left"),
                    right = DeviceAssociation(deviceId = "right-1", displayName = "Right"),
                ),
            heartRateSource =
                HeartRateSource(
                    source = DeviceAssociation(deviceId = "hr-1", displayName = "HR"),
                ),
            syncState = SyncState.EXPORT_READY,
            interruptionDetected = false,
        )

    private fun sample() =
        RideSample(
            timestampEpochSeconds = 123L,
            leftPowerWatts = 100,
            rightPowerWatts = 110,
            totalPowerWatts = 210,
            cadenceRpm = 90,
            heartRateBpm = 150,
            zoneIndex = 3,
            leftConnected = true,
            rightConnected = true,
            heartRateConnected = true,
        )

    private fun summary() =
        DerivedSummary(
            averagePowerWatts = 205,
            maxPowerWatts = 310,
            averageCadenceRpm = 91,
            averageHeartRateBpm = 151,
            averageBalancePercentLeft = 49,
            timeInZoneSeconds = mapOf(2 to 60, 3 to 40),
            asymmetryIntervals =
                listOf(
                    AsymmetryInterval("00:30", "00:59", leftPercent = 54, rightPercent = 46, supported = true),
                    AsymmetryInterval("01:20", "01:49", leftPercent = 45, rightPercent = 55, supported = false),
                ),
            partialHeartRate = false,
            partialBalance = true,
            exportState = SyncState.EXPORT_READY,
        )
}

private class FakeRideDao : RideDao {
    val sessions = mutableMapOf<String, RideSessionEntity>()
    val samples = mutableListOf<RideSampleEntity>()
    val summaries = mutableMapOf<String, DerivedSummaryEntity>()

    override suspend fun upsertSession(session: RideSessionEntity) {
        sessions[session.rideId] = session
    }

    override suspend fun insertSamples(samples: List<RideSampleEntity>) {
        this.samples += samples
    }

    override suspend fun upsertSummary(summary: DerivedSummaryEntity) {
        summaries[summary.rideId] = summary
    }

    override suspend fun session(rideId: String): RideSessionEntity? = sessions[rideId]

    override suspend fun samplesForRide(rideId: String): List<RideSampleEntity> =
        samples.filter { it.rideId == rideId }

    override suspend fun summary(rideId: String): DerivedSummaryEntity? = summaries[rideId]
}
