package com.sjlangley.peleotonpowermeter.ble

import com.sjlangley.peleotonpowermeter.data.model.RideSample
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BleSampleCollectorTest {
    private lateinit var collector: BleSampleCollector

    @Before
    fun setup() {
        collector = BleSampleCollector(ftpWatts = 200)
    }

    @Test
    fun generateSample_withNoPowerData_returnsZeroPower() = runTest {
        val sample = collector.generateSample(1000L)

        assertEquals(0, sample.totalPowerWatts)
        assertNull(sample.leftPowerWatts)
        assertNull(sample.rightPowerWatts)
        assertNull(sample.cadenceRpm)
        assertNull(sample.heartRateBpm)
        assertFalse(sample.leftConnected)
        assertFalse(sample.rightConnected)
        assertFalse(sample.heartRateConnected)
    }

    @Test
    fun generateSample_withLeftPowerOnly_usesLeftPower() = runTest {
        val powerData = CyclingPowerData(
            instantaneousPower = 150,
            crankRevolutions = 100,
            lastCrankEventTime = 1024,
        )

        collector.updateLeftPower(powerData)
        collector.setLeftConnected(true)

        val sample = collector.generateSample(1000L)

        assertEquals(150, sample.totalPowerWatts)
        assertEquals(150, sample.leftPowerWatts)
        assertNull(sample.rightPowerWatts)
        assertTrue(sample.leftConnected)
        assertFalse(sample.rightConnected)
    }

    @Test
    fun generateSample_withRightPowerOnly_usesRightPower() = runTest {
        val powerData = CyclingPowerData(
            instantaneousPower = 160,
            crankRevolutions = 100,
            lastCrankEventTime = 1024,
        )

        collector.updateRightPower(powerData)
        collector.setRightConnected(true)

        val sample = collector.generateSample(1000L)

        assertEquals(160, sample.totalPowerWatts)
        assertNull(sample.leftPowerWatts)
        assertEquals(160, sample.rightPowerWatts)
        assertFalse(sample.leftConnected)
        assertTrue(sample.rightConnected)
    }

    @Test
    fun generateSample_withBothPedals_sumsPower() = runTest {
        val leftPower = CyclingPowerData(
            instantaneousPower = 75,
            crankRevolutions = 100,
            lastCrankEventTime = 1024,
        )
        val rightPower = CyclingPowerData(
            instantaneousPower = 85,
            crankRevolutions = 100,
            lastCrankEventTime = 1024,
        )

        collector.updateLeftPower(leftPower)
        collector.updateRightPower(rightPower)
        collector.setLeftConnected(true)
        collector.setRightConnected(true)

        val sample = collector.generateSample(1000L)

        assertEquals(160, sample.totalPowerWatts)
        assertEquals(75, sample.leftPowerWatts)
        assertEquals(85, sample.rightPowerWatts)
        assertTrue(sample.leftConnected)
        assertTrue(sample.rightConnected)
    }

    @Test
    fun generateSample_withCadenceData_calculatesCadence() = runTest {
        // First measurement: 100 revolutions at 1024/1024 second (1 second)
        val powerData1 = CyclingPowerData(
            instantaneousPower = 150,
            crankRevolutions = 100,
            lastCrankEventTime = 1024,
        )
        // Second measurement: 102 revolutions at 3072/1024 seconds (3 seconds)
        // Delta: 2 revolutions in 2 seconds = 60 RPM
        val powerData2 = CyclingPowerData(
            instantaneousPower = 150,
            crankRevolutions = 102,
            lastCrankEventTime = 3072,
        )

        collector.updateRightPower(powerData1)
        collector.setRightConnected(true)
        val sample1 = collector.generateSample(1000L)
        assertNull(sample1.cadenceRpm) // No previous data for cadence calc

        collector.updateRightPower(powerData2)
        val sample2 = collector.generateSample(1002L)
        assertEquals(60, sample2.cadenceRpm) // 2 rev in 2 sec = 60 RPM
    }

    @Test
    fun generateSample_withHeartRate_includesHeartRate() = runTest {
        val heartRateData = HeartRateData(
            heartRateBpm = 145,
        )

        collector.updateHeartRate(heartRateData)
        collector.setHeartRateConnected(true)

        val sample = collector.generateSample(1000L)

        assertEquals(145, sample.heartRateBpm)
        assertTrue(sample.heartRateConnected)
    }

    @Test
    fun calculateZoneIndex_zone1_activeRecovery() = runTest {
        // Zone 1: <55% FTP = <110 watts for FTP 200
        val powerData = CyclingPowerData(instantaneousPower = 100)
        collector.updateLeftPower(powerData)

        val sample = collector.generateSample(1000L)

        assertEquals(0, sample.zoneIndex) // Zone 1
    }

    @Test
    fun calculateZoneIndex_zone2_endurance() = runTest {
        // Zone 2: 55-74% FTP = 110-148 watts for FTP 200
        val powerData = CyclingPowerData(instantaneousPower = 130)
        collector.updateLeftPower(powerData)

        val sample = collector.generateSample(1000L)

        assertEquals(1, sample.zoneIndex) // Zone 2
    }

    @Test
    fun calculateZoneIndex_zone3_tempo() = runTest {
        // Zone 3: 75-89% FTP = 150-178 watts for FTP 200
        val powerData = CyclingPowerData(instantaneousPower = 160)
        collector.updateLeftPower(powerData)

        val sample = collector.generateSample(1000L)

        assertEquals(2, sample.zoneIndex) // Zone 3
    }

    @Test
    fun calculateZoneIndex_zone4_threshold() = runTest {
        // Zone 4: 90-104% FTP = 180-208 watts for FTP 200
        val powerData = CyclingPowerData(instantaneousPower = 195)
        collector.updateLeftPower(powerData)

        val sample = collector.generateSample(1000L)

        assertEquals(3, sample.zoneIndex) // Zone 4
    }

    @Test
    fun calculateZoneIndex_zone5_vo2Max() = runTest {
        // Zone 5: 105-119% FTP = 210-238 watts for FTP 200
        val powerData = CyclingPowerData(instantaneousPower = 220)
        collector.updateLeftPower(powerData)

        val sample = collector.generateSample(1000L)

        assertEquals(4, sample.zoneIndex) // Zone 5
    }

    @Test
    fun calculateZoneIndex_zone6_anaerobic() = runTest {
        // Zone 6: 120-149% FTP = 240-298 watts for FTP 200
        val powerData = CyclingPowerData(instantaneousPower = 260)
        collector.updateLeftPower(powerData)

        val sample = collector.generateSample(1000L)

        assertEquals(5, sample.zoneIndex) // Zone 6
    }

    @Test
    fun calculateZoneIndex_zone7_neuromuscular() = runTest {
        // Zone 7: >=150% FTP = >=300 watts for FTP 200
        val powerData = CyclingPowerData(instantaneousPower = 350)
        collector.updateLeftPower(powerData)

        val sample = collector.generateSample(1000L)

        assertEquals(6, sample.zoneIndex) // Zone 7
    }

    @Test
    fun setLeftConnected_false_clearsLeftData() = runTest {
        val powerData = CyclingPowerData(instantaneousPower = 150)
        collector.updateLeftPower(powerData)
        collector.setLeftConnected(true)

        var sample = collector.generateSample(1000L)
        assertEquals(150, sample.leftPowerWatts)

        collector.setLeftConnected(false)
        sample = collector.generateSample(1001L)
        assertNull(sample.leftPowerWatts)
        assertFalse(sample.leftConnected)
    }

    @Test
    fun setRightConnected_false_clearsRightData() = runTest {
        val powerData = CyclingPowerData(instantaneousPower = 160)
        collector.updateRightPower(powerData)
        collector.setRightConnected(true)

        var sample = collector.generateSample(1000L)
        assertEquals(160, sample.rightPowerWatts)

        collector.setRightConnected(false)
        sample = collector.generateSample(1001L)
        assertNull(sample.rightPowerWatts)
        assertFalse(sample.rightConnected)
    }

    @Test
    fun setHeartRateConnected_false_clearsHeartRateData() = runTest {
        val heartRate = HeartRateData(heartRateBpm = 145)
        collector.updateHeartRate(heartRate)
        collector.setHeartRateConnected(true)

        var sample = collector.generateSample(1000L)
        assertEquals(145, sample.heartRateBpm)

        collector.setHeartRateConnected(false)
        sample = collector.generateSample(1001L)
        assertNull(sample.heartRateBpm)
        assertFalse(sample.heartRateConnected)
    }

    @Test
    fun reset_clearsAllData() = runTest {
        // Set up collector with data
        collector.updateLeftPower(CyclingPowerData(instantaneousPower = 150))
        collector.updateRightPower(CyclingPowerData(instantaneousPower = 160))
        collector.updateHeartRate(HeartRateData(heartRateBpm = 145))
        collector.setLeftConnected(true)
        collector.setRightConnected(true)
        collector.setHeartRateConnected(true)

        collector.reset()

        val sample = collector.generateSample(1000L)
        assertEquals(0, sample.totalPowerWatts)
        assertNull(sample.leftPowerWatts)
        assertNull(sample.rightPowerWatts)
        assertNull(sample.cadenceRpm)
        assertNull(sample.heartRateBpm)
        assertFalse(sample.leftConnected)
        assertFalse(sample.rightConnected)
        assertFalse(sample.heartRateConnected)
    }
}
