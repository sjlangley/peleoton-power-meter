package com.sjlangley.peleotonpowermeter.domain

import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.SyncState
import kotlin.math.roundToInt

object RideSummaryCalculator {
    fun calculate(samples: List<RideSample>): DerivedSummary {
        require(samples.isNotEmpty()) { "Ride summary requires at least one sample." }

        // PR1 persists one logical sample frame per second, so simple aggregates
        // map directly to "seconds spent" style summary metrics.
        val totalPowerValues = samples.map { it.totalPowerWatts }
        val cadenceValues = samples.mapNotNull { it.cadenceRpm }
        val heartRateValues = samples.mapNotNull { it.heartRateBpm }
        val balanceValues = samples.mapNotNull { it.balancePercentLeft }
        val timeInZoneSeconds = samples.groupingBy { it.zoneIndex }.eachCount()
        val partialHeartRate = samples.any { !it.heartRateConnected }
        val partialBalance = samples.any { !it.leftConnected || !it.rightConnected }
        val asymmetryIntervals = AsymmetryAnalyzer.analyze(samples)

        return DerivedSummary(
            averagePowerWatts = totalPowerValues.average().roundToInt(),
            maxPowerWatts = totalPowerValues.max(),
            averageCadenceRpm = cadenceValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            averageHeartRateBpm = heartRateValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            averageBalancePercentLeft = balanceValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            timeInZoneSeconds = timeInZoneSeconds,
            asymmetryIntervals = asymmetryIntervals,
            partialHeartRate = partialHeartRate,
            partialBalance = partialBalance,
            exportState = SyncState.EXPORT_READY,
        )
    }
}
