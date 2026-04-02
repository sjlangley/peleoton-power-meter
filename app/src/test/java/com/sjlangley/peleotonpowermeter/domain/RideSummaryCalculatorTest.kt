package com.sjlangley.peleotonpowermeter.domain

import com.sjlangley.peleotonpowermeter.data.model.RideSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSummaryCalculatorTest {
    @Test
    fun calculate_returnsAverageAndMaxValues() {
        val samples =
            listOf(
                sampleAt(0, totalPower = 150, cadence = 85, heartRate = 140),
                sampleAt(1, totalPower = 210, cadence = 90, heartRate = 150),
                sampleAt(2, totalPower = 240, cadence = 95, heartRate = 160),
            )

        val summary = RideSummaryCalculator.calculate(samples)

        assertEquals(200, summary.averagePowerWatts)
        assertEquals(240, summary.maxPowerWatts)
        assertEquals(90, summary.averageCadenceRpm)
        assertEquals(150, summary.averageHeartRateBpm)
        assertFalse(summary.partialHeartRate)
        assertFalse(summary.partialBalance)
    }

    @Test
    fun calculate_marksPartialDataWhenPedalOrHeartRateDrops() {
        val samples =
            listOf(
                sampleAt(0, totalPower = 180, cadence = 88, heartRate = 145),
                sampleAt(
                    1,
                    totalPower = 182,
                    cadence = 89,
                    heartRate = null,
                    leftConnected = false,
                ),
            )

        val summary = RideSummaryCalculator.calculate(samples)

        assertTrue(summary.partialHeartRate)
        assertTrue(summary.partialBalance)
    }

    private fun sampleAt(
        second: Long,
        totalPower: Int,
        cadence: Int?,
        heartRate: Int?,
        leftConnected: Boolean = true,
        rightConnected: Boolean = true,
    ): RideSample =
        RideSample(
            timestampEpochSeconds = second,
            leftPowerWatts = if (leftConnected) totalPower / 2 else null,
            rightPowerWatts = if (rightConnected) totalPower / 2 else null,
            totalPowerWatts = totalPower,
            cadenceRpm = cadence,
            heartRateBpm = heartRate,
            zoneIndex = 3,
            leftConnected = leftConnected,
            rightConnected = rightConnected,
            heartRateConnected = heartRate != null,
        )
}
