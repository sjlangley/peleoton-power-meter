package com.sjlangley.peleotonpowermeter.ui

import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.SummaryUiState

object SummaryUiStateFactory {
    fun fromRideData(
        samples: List<RideSample>,
        summary: DerivedSummary,
    ): SummaryUiState =
        SummaryUiState(
            rideLabel = "${samples.rideDurationSeconds().asDurationLabel()} indoor ride",
            averagePowerLabel = "${summary.averagePowerWatts} W",
            averageCadenceLabel = summary.averageCadenceRpm?.let { "$it rpm" } ?: "--",
            averageHeartRateLabel = summary.averageHeartRateBpm?.let { "$it bpm" } ?: "--",
            asymmetryIntervals = summary.asymmetryIntervals,
            asymmetryMessage = summary.asymmetryMessage(),
            exportLabel = "Share Demo Summary",
            resetLabel = "Start Another Demo Ride",
        )

    private fun DerivedSummary.asymmetryMessage(): String =
        when {
            partialBalance ->
                "Balance insight is limited during partial pedal data. Unsupported intervals stay suppressed."
            asymmetryIntervals.isEmpty() ->
                "No sustained asymmetry intervals detected."
            else ->
                "Asymmetry is derived post-ride from sustained full-data intervals."
        }

    private fun List<RideSample>.rideDurationSeconds(): Long =
        if (isEmpty()) {
            0
        } else {
            (last().timestampEpochSeconds - first().timestampEpochSeconds) + 1
        }

    private fun Long.asDurationLabel(): String {
        val minutes = this / 60
        val seconds = this % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
