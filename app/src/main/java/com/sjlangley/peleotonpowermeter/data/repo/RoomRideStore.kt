package com.sjlangley.peleotonpowermeter.data.repo

import com.sjlangley.peleotonpowermeter.data.local.DerivedSummaryEntity
import com.sjlangley.peleotonpowermeter.data.local.RideDao
import com.sjlangley.peleotonpowermeter.data.local.RideSampleEntity
import com.sjlangley.peleotonpowermeter.data.local.RideSessionEntity
import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.RideSession

class RoomRideStore(
    private val rideDao: RideDao,
) : RideStore {
    override suspend fun startSession(session: RideSession) {
        rideDao.upsertSession(session.toEntity())
    }

    override suspend fun appendSample(
        rideId: String,
        sample: RideSample,
    ) {
        rideDao.insertSamples(listOf(sample.toEntity(rideId)))
    }

    override suspend fun finishSession(
        rideId: String,
        endedAtEpochSeconds: Long,
    ) {
        val existingSession =
            checkNotNull(rideDao.session(rideId)) {
                "Cannot finish ride $rideId before a session has been started."
            }

        rideDao.upsertSession(existingSession.copy(endedAtEpochSeconds = endedAtEpochSeconds))
    }

    override suspend fun saveSummary(
        rideId: String,
        summary: DerivedSummary,
    ) {
        rideDao.upsertSummary(summary.toEntity(rideId))
    }

    override suspend fun loadSession(rideId: String): RideSession? =
        rideDao.session(rideId)?.toModel()

    override suspend fun loadSamples(rideId: String): List<RideSample> =
        rideDao.samplesForRide(rideId).map(RideSampleEntity::toModel)

    override suspend fun loadSummary(rideId: String): DerivedSummary? =
        rideDao.summary(rideId)?.toModel()
}

private fun RideSession.toEntity(): RideSessionEntity =
    RideSessionEntity(
        rideId = rideId,
        startedAtEpochSeconds = startedAtEpochSeconds,
        endedAtEpochSeconds = endedAtEpochSeconds,
        ftpWatts = ftpWatts,
        leftDeviceId = pedalPair.left?.deviceId,
        rightDeviceId = pedalPair.right?.deviceId,
        heartRateDeviceId = heartRateSource.source?.deviceId,
        syncState = syncState,
        interruptionDetected = interruptionDetected,
    )

private fun RideSample.toEntity(rideId: String): RideSampleEntity =
    RideSampleEntity(
        rideId = rideId,
        timestampEpochSeconds = timestampEpochSeconds,
        leftPowerWatts = leftPowerWatts,
        rightPowerWatts = rightPowerWatts,
        totalPowerWatts = totalPowerWatts,
        cadenceRpm = cadenceRpm,
        heartRateBpm = heartRateBpm,
        zoneIndex = zoneIndex,
        leftConnected = leftConnected,
        rightConnected = rightConnected,
        heartRateConnected = heartRateConnected,
    )

private fun DerivedSummary.toEntity(rideId: String): DerivedSummaryEntity =
    DerivedSummaryEntity(
        rideId = rideId,
        averagePowerWatts = averagePowerWatts,
        maxPowerWatts = maxPowerWatts,
        averageCadenceRpm = averageCadenceRpm,
        averageHeartRateBpm = averageHeartRateBpm,
        averageBalancePercentLeft = averageBalancePercentLeft,
        timeInZoneSeconds = timeInZoneSeconds,
        asymmetryIntervals = asymmetryIntervals,
        partialHeartRate = partialHeartRate,
        partialBalance = partialBalance,
        exportState = exportState,
    )

private fun RideSessionEntity.toModel(): RideSession =
    RideSession(
        rideId = rideId,
        startedAtEpochSeconds = startedAtEpochSeconds,
        endedAtEpochSeconds = endedAtEpochSeconds,
        ftpWatts = ftpWatts,
        pedalPair =
            com.sjlangley.peleotonpowermeter.data.model.PedalPair(
                left = leftDeviceId?.asDeviceAssociation(),
                right = rightDeviceId?.asDeviceAssociation(),
            ),
        heartRateSource =
            com.sjlangley.peleotonpowermeter.data.model.HeartRateSource(
                source = heartRateDeviceId?.asDeviceAssociation(),
            ),
        syncState = syncState,
        interruptionDetected = interruptionDetected,
    )

private fun RideSampleEntity.toModel(): RideSample =
    RideSample(
        timestampEpochSeconds = timestampEpochSeconds,
        leftPowerWatts = leftPowerWatts,
        rightPowerWatts = rightPowerWatts,
        totalPowerWatts = totalPowerWatts,
        cadenceRpm = cadenceRpm,
        heartRateBpm = heartRateBpm,
        zoneIndex = zoneIndex,
        leftConnected = leftConnected,
        rightConnected = rightConnected,
        heartRateConnected = heartRateConnected,
    )

private fun DerivedSummaryEntity.toModel(): DerivedSummary =
    DerivedSummary(
        averagePowerWatts = averagePowerWatts,
        maxPowerWatts = maxPowerWatts,
        averageCadenceRpm = averageCadenceRpm,
        averageHeartRateBpm = averageHeartRateBpm,
        averageBalancePercentLeft = averageBalancePercentLeft,
        timeInZoneSeconds = timeInZoneSeconds,
        asymmetryIntervals = asymmetryIntervals,
        partialHeartRate = partialHeartRate,
        partialBalance = partialBalance,
        exportState = exportState,
    )

private fun String.asDeviceAssociation() =
    com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation(
        deviceId = this,
        displayName = this,
    )
