package com.sjlangley.peleotonpowermeter.domain

import com.sjlangley.peleotonpowermeter.data.model.AsymmetryInterval
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import kotlin.math.abs
import kotlin.math.roundToInt

object AsymmetryAnalyzer {
    fun analyze(samples: List<RideSample>): List<AsymmetryInterval> {
        if (samples.size < MINIMUM_INTERVAL_SECONDS) {
            return emptyList()
        }

        return findQualifiedIntervals(samples)
            .sortedWith(
                compareByDescending<QualifiedInterval> { abs(it.leftPercent - it.rightPercent) }
                    .thenByDescending { it.durationSeconds }
                    .thenBy { it.startTimestampEpochSeconds },
            )
            .take(MAX_NOTABLE_INTERVALS)
            .map { interval ->
                AsymmetryInterval(
                    startLabel = interval.startTimestampEpochSeconds.asElapsedLabel(),
                    endLabel = interval.endTimestampEpochSeconds.asElapsedLabel(),
                    leftPercent = interval.leftPercent,
                    rightPercent = interval.rightPercent,
                    supported = true,
                )
            }
    }

    private fun findQualifiedIntervals(
        samples: List<RideSample>,
    ): List<QualifiedInterval> {
        val intervals = mutableListOf<QualifiedInterval>()
        var currentDirection: Direction? = null
        var intervalStartIndex: Int? = null

        samples.forEachIndexed { index, sample ->
            val sampleDirection = sample.directionOrNull()
            if (sampleDirection == currentDirection) {
                return@forEachIndexed
            }

            intervalStartIndex?.let { startIndex ->
                qualifiedIntervalOrNull(samples, startIndex, index - 1)?.let(intervals::add)
            }

            currentDirection = sampleDirection
            intervalStartIndex = sampleDirection?.let { index }
        }

        intervalStartIndex?.let { startIndex ->
            qualifiedIntervalOrNull(samples, startIndex, samples.lastIndex)?.let(intervals::add)
        }

        return intervals
    }

    private fun qualifiedIntervalOrNull(
        samples: List<RideSample>,
        startIndex: Int,
        endIndex: Int,
    ): QualifiedInterval? {
        val intervalSamples = samples.subList(startIndex, endIndex + 1)
        if (intervalSamples.size < MINIMUM_INTERVAL_SECONDS) {
            return null
        }

        val averageLeft = intervalSamples.mapNotNull { it.balancePercentLeft }.average().roundToInt()
        val averageGap = abs((averageLeft * 2) - 100)
        if (averageGap <= MINIMUM_BALANCE_GAP_PERCENT) {
            return null
        }

        return QualifiedInterval(
            startTimestampEpochSeconds = samples[startIndex].timestampEpochSeconds,
            endTimestampEpochSeconds = samples[endIndex].timestampEpochSeconds,
            leftPercent = averageLeft,
            rightPercent = 100 - averageLeft,
        )
    }

    private fun Long.asElapsedLabel(): String {
        val minutes = this / 60
        val seconds = this % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun RideSample.directionOrNull(): Direction? {
        if (!leftConnected || !rightConnected) {
            return null
        }

        val balancePercentLeft = balancePercentLeft ?: return null
        val gap = abs((balancePercentLeft * 2) - 100)
        if (gap <= MINIMUM_BALANCE_GAP_PERCENT) {
            return null
        }

        return if (balancePercentLeft >= 50) Direction.LEFT else Direction.RIGHT
    }

    private enum class Direction {
        LEFT,
        RIGHT,
    }

    private data class QualifiedInterval(
        val startTimestampEpochSeconds: Long,
        val endTimestampEpochSeconds: Long,
        val leftPercent: Int,
        val rightPercent: Int,
    ) {
        val durationSeconds: Long
            get() = (endTimestampEpochSeconds - startTimestampEpochSeconds) + 1
    }

    private const val MINIMUM_INTERVAL_SECONDS = 30
    private const val MINIMUM_BALANCE_GAP_PERCENT = 3
    private const val MAX_NOTABLE_INTERVALS = 3
}
