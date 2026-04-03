package com.sjlangley.peleotonpowermeter.domain

import com.sjlangley.peleotonpowermeter.data.model.AsymmetryInterval
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

    @Test
    fun calculate_returnsTopThreeRankedAsymmetryIntervals() {
        val samples = buildAsymmetryFixture()

        val summary = RideSummaryCalculator.calculate(samples)

        assertEquals(
            listOf(
                interval("02:10", "02:39", leftPercent = 44, rightPercent = 56),
                interval("00:00", "00:39", leftPercent = 54, rightPercent = 46),
                interval("01:10", "01:39", leftPercent = 47, rightPercent = 53),
            ),
            summary.asymmetryIntervals,
        )
    }

    @Test
    fun calculate_suppressesShortAndUnsupportedAsymmetryIntervals() {
        val cleanWindow =
            (0L until 29L).map { second ->
                sampleAt(second, leftPower = 54, rightPower = 46, heartRate = 145)
            }
        val unsupportedWindow =
            (29L until 59L).map { second ->
                sampleAt(
                    second,
                    leftPower = 56,
                    rightPower = 44,
                    heartRate = 145,
                    leftConnected = false,
                )
            }

        val summary = RideSummaryCalculator.calculate(cleanWindow + unsupportedWindow)

        assertTrue(summary.partialBalance)
        assertTrue(summary.asymmetryIntervals.isEmpty())
    }

    private fun sampleAt(
        second: Long,
        totalPower: Int = 200,
        cadence: Int? = 90,
        heartRate: Int?,
        leftPower: Int? = if (totalPower % 2 == 0) totalPower / 2 else null,
        rightPower: Int? = if (totalPower % 2 == 0) totalPower / 2 else null,
        leftConnected: Boolean = true,
        rightConnected: Boolean = true,
    ): RideSample {
        val resolvedTotalPower =
            listOfNotNull(
                leftPower.takeIf { leftConnected },
                rightPower.takeIf { rightConnected },
            ).sum().takeIf { it > 0 } ?: totalPower

        return RideSample(
            timestampEpochSeconds = second,
            leftPowerWatts = if (leftConnected) leftPower else null,
            rightPowerWatts = if (rightConnected) rightPower else null,
            totalPowerWatts = resolvedTotalPower,
            cadenceRpm = cadence,
            heartRateBpm = heartRate,
            zoneIndex = 3,
            leftConnected = leftConnected,
            rightConnected = rightConnected,
            heartRateConnected = heartRate != null,
        )
    }

    private fun buildAsymmetryFixture(): List<RideSample> =
        buildList {
            addAll(
                (0L until 40L).map { second ->
                    sampleAt(second, leftPower = 54, rightPower = 46, heartRate = 145)
                },
            )
            addAll(
                (40L until 70L).map { second ->
                    sampleAt(second, leftPower = 50, rightPower = 50, heartRate = 146)
                },
            )
            addAll(
                (70L until 100L).map { second ->
                    sampleAt(second, leftPower = 47, rightPower = 53, heartRate = 147)
                },
            )
            addAll(
                (100L until 130L).map { second ->
                    sampleAt(second, leftPower = 50, rightPower = 50, heartRate = 148)
                },
            )
            addAll(
                (130L until 160L).map { second ->
                    sampleAt(second, leftPower = 44, rightPower = 56, heartRate = 149)
                },
            )
        }

    private fun interval(
        startLabel: String,
        endLabel: String,
        leftPercent: Int,
        rightPercent: Int,
    ) = AsymmetryInterval(
        startLabel = startLabel,
        endLabel = endLabel,
        leftPercent = leftPercent,
        rightPercent = rightPercent,
        supported = true,
    )
}
