package com.sjlangley.peleotonpowermeter.fit

import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.DeviceIndex
import com.garmin.fit.DeviceInfoMesg
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.File
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FileEncoder
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.RideSession
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong
import java.io.File as JavaFile

object FitActivityFileEncoder {
    fun encode(
        session: RideSession,
        samples: List<RideSample>,
        summary: DerivedSummary,
    ): ByteArray {
        val tempFile = kotlin.io.path.createTempFile(prefix = "peleoton-fit-", suffix = ".fit").toFile()
        return try {
            writeTo(tempFile, session, samples, summary)
            tempFile.readBytes()
        } finally {
            tempFile.delete()
        }
    }

    fun writeTo(
        outputFile: JavaFile,
        session: RideSession,
        samples: List<RideSample>,
        summary: DerivedSummary,
    ) {
        require(samples.isNotEmpty()) { "FIT export requires at least one stored ride sample." }

        val startEpochSeconds = samples.first().timestampEpochSeconds
        val endEpochSeconds = session.endedAtEpochSeconds ?: samples.last().timestampEpochSeconds
        val stopEpochSeconds = endEpochSeconds + 1
        val totalElapsedSeconds = (stopEpochSeconds - startEpochSeconds).coerceAtLeast(1).toFloat()
        val totalCycles = samples.totalCycles()
        val encoder = FileEncoder(outputFile, Fit.ProtocolVersion.V2_0)

        try {
            encoder.write(fileIdMesg(session, startEpochSeconds))
            encoder.write(deviceInfoMesg(session, startEpochSeconds))
            encoder.write(timerEventMesg(startEpochSeconds, EventType.START))
            samples.forEach { sample ->
                encoder.write(recordMesg(sample))
            }
            encoder.write(timerEventMesg(stopEpochSeconds, EventType.STOP_ALL))
            encoder.write(
                lapMesg(
                    startEpochSeconds = startEpochSeconds,
                    stopEpochSeconds = stopEpochSeconds,
                    totalElapsedSeconds = totalElapsedSeconds,
                    totalCycles = totalCycles,
                    averageHeartRateBpm = summary.averageHeartRateBpm,
                    maxHeartRateBpm = samples.maxOfOrNull { sample -> sample.heartRateBpm ?: 0 }?.takeIf { it > 0 },
                    averageCadenceRpm = summary.averageCadenceRpm,
                    maxCadenceRpm = samples.maxOfOrNull { sample -> sample.cadenceRpm ?: 0 }?.takeIf { it > 0 },
                    averagePowerWatts = summary.averagePowerWatts,
                    maxPowerWatts = summary.maxPowerWatts,
                ),
            )
            encoder.write(
                sessionMesg(
                    startEpochSeconds = startEpochSeconds,
                    stopEpochSeconds = stopEpochSeconds,
                    totalElapsedSeconds = totalElapsedSeconds,
                    totalCycles = totalCycles,
                    averageHeartRateBpm = summary.averageHeartRateBpm,
                    maxHeartRateBpm = samples.maxOfOrNull { sample -> sample.heartRateBpm ?: 0 }?.takeIf { it > 0 },
                    averageCadenceRpm = summary.averageCadenceRpm,
                    maxCadenceRpm = samples.maxOfOrNull { sample -> sample.cadenceRpm ?: 0 }?.takeIf { it > 0 },
                    averagePowerWatts = summary.averagePowerWatts,
                    maxPowerWatts = summary.maxPowerWatts,
                ),
            )
            encoder.write(activityMesg(stopEpochSeconds, totalElapsedSeconds))
        } finally {
            encoder.close()
        }
    }

    private fun fileIdMesg(
        session: RideSession,
        startEpochSeconds: Long,
    ): FileIdMesg =
        FileIdMesg().apply {
            setType(File.ACTIVITY)
            setManufacturer(Manufacturer.DEVELOPMENT)
            setProduct(PRODUCT_ID)
            setTimeCreated(dateTime(startEpochSeconds))
            setProductName(PRODUCT_NAME)
            setSerialNumber(session.serialNumber())
        }

    private fun deviceInfoMesg(
        session: RideSession,
        startEpochSeconds: Long,
    ): DeviceInfoMesg =
        DeviceInfoMesg().apply {
            setDeviceIndex(DeviceIndex.CREATOR)
            setManufacturer(Manufacturer.DEVELOPMENT)
            setProduct(PRODUCT_ID)
            setProductName(PRODUCT_NAME.take(20))
            setSerialNumber(session.serialNumber())
            setSoftwareVersion(SOFTWARE_VERSION)
            setTimestamp(dateTime(startEpochSeconds))
        }

    private fun timerEventMesg(
        epochSeconds: Long,
        eventType: EventType,
    ): EventMesg =
        EventMesg().apply {
            setTimestamp(dateTime(epochSeconds))
            setEvent(Event.TIMER)
            setEventType(eventType)
        }

    private fun recordMesg(sample: RideSample): RecordMesg =
        RecordMesg().apply {
            setTimestamp(dateTime(sample.timestampEpochSeconds))
            setPower(sample.totalPowerWatts)
            sample.cadenceRpm?.let { setCadence(it.toShort()) }
            sample.heartRateBpm?.let { setHeartRate(it.toShort()) }
        }

    private fun lapMesg(
        startEpochSeconds: Long,
        stopEpochSeconds: Long,
        totalElapsedSeconds: Float,
        totalCycles: Long?,
        averageHeartRateBpm: Int?,
        maxHeartRateBpm: Int?,
        averageCadenceRpm: Int?,
        maxCadenceRpm: Int?,
        averagePowerWatts: Int,
        maxPowerWatts: Int,
    ): LapMesg =
        LapMesg().apply {
            setMessageIndex(0)
            setTimestamp(dateTime(stopEpochSeconds))
            setStartTime(dateTime(startEpochSeconds))
            setTotalElapsedTime(totalElapsedSeconds)
            setTotalTimerTime(totalElapsedSeconds)
            totalCycles?.let(::setTotalCycles)
            averageHeartRateBpm?.let { setAvgHeartRate(it.toShort()) }
            maxHeartRateBpm?.let { setMaxHeartRate(it.toShort()) }
            averageCadenceRpm?.let { setAvgCadence(it.toShort()) }
            maxCadenceRpm?.let { setMaxCadence(it.toShort()) }
            setAvgPower(averagePowerWatts)
            setMaxPower(maxPowerWatts)
            setSport(Sport.CYCLING)
            setSubSport(SubSport.INDOOR_CYCLING)
        }

    private fun sessionMesg(
        startEpochSeconds: Long,
        stopEpochSeconds: Long,
        totalElapsedSeconds: Float,
        totalCycles: Long?,
        averageHeartRateBpm: Int?,
        maxHeartRateBpm: Int?,
        averageCadenceRpm: Int?,
        maxCadenceRpm: Int?,
        averagePowerWatts: Int,
        maxPowerWatts: Int,
    ): SessionMesg =
        SessionMesg().apply {
            setMessageIndex(0)
            setTimestamp(dateTime(stopEpochSeconds))
            setStartTime(dateTime(startEpochSeconds))
            setSport(Sport.CYCLING)
            setSubSport(SubSport.INDOOR_CYCLING)
            setFirstLapIndex(0)
            setNumLaps(1)
            setTotalElapsedTime(totalElapsedSeconds)
            setTotalTimerTime(totalElapsedSeconds)
            totalCycles?.let(::setTotalCycles)
            averageHeartRateBpm?.let { setAvgHeartRate(it.toShort()) }
            maxHeartRateBpm?.let { setMaxHeartRate(it.toShort()) }
            averageCadenceRpm?.let { setAvgCadence(it.toShort()) }
            maxCadenceRpm?.let { setMaxCadence(it.toShort()) }
            setAvgPower(averagePowerWatts)
            setMaxPower(maxPowerWatts)
        }

    private fun activityMesg(
        stopEpochSeconds: Long,
        totalElapsedSeconds: Float,
    ): ActivityMesg =
        ActivityMesg().apply {
            val timestamp = dateTime(stopEpochSeconds)
            setTimestamp(timestamp)
            setTotalTimerTime(totalElapsedSeconds)
            setNumSessions(1)
            setLocalTimestamp(
                timestamp.timestamp + timestamp.instant.atZone(ZoneId.systemDefault()).offset.totalSeconds,
            )
        }

    private fun dateTime(epochSeconds: Long): DateTime =
        DateTime(Instant.ofEpochSecond(epochSeconds))

    private fun List<RideSample>.totalCycles(): Long? {
        val cadenceSamples = mapNotNull { sample -> sample.cadenceRpm }
        if (cadenceSamples.isEmpty()) {
            return null
        }

        return (cadenceSamples.sum().toDouble() / 60.0).roundToLong()
    }

    private fun RideSession.serialNumber(): Long =
        rideId.hashCode().toLong() and 0xffffffffL

    private const val PRODUCT_ID = 1
    private const val PRODUCT_NAME = "Peleoton Power Meter"
    private const val SOFTWARE_VERSION = 1.0f
}
