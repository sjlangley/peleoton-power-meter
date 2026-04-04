package com.sjlangley.peleotonpowermeter.ui

import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.SyncState
import com.sjlangley.peleotonpowermeter.data.model.SummaryUiState

object SummaryUiStateFactory {
    fun fromRideData(
        rideId: String,
        samples: List<RideSample>,
        summary: DerivedSummary,
    ): SummaryUiState =
        SummaryUiState(
            rideId = rideId,
            rideLabel = "${samples.rideDurationSeconds().asDurationLabel()} indoor ride",
            averagePowerLabel = "${summary.averagePowerWatts} W",
            averageCadenceLabel = summary.averageCadenceRpm?.let { "$it rpm" } ?: "--",
            averageHeartRateLabel = summary.averageHeartRateBpm?.let { "$it bpm" } ?: "--",
            asymmetryIntervals = summary.asymmetryIntervals,
            asymmetryMessage = summary.asymmetryMessage(),
            exportLabel = summary.exportLabel(),
            exportStatusMessage = summary.exportStatusMessage(),
            resetLabel = "Start Another Ride",
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

    private fun DerivedSummary.exportLabel(): String =
        when (exportState) {
            SyncState.EXPORT_FAILED -> "Retry FIT Export"
            SyncState.EXPORTED -> "Export FIT Again"
            SyncState.EXPORT_READY,
            SyncState.LOCAL_ONLY,
            -> "Export FIT"
        }

    private fun DerivedSummary.exportStatusMessage(): String =
        when (exportState) {
            SyncState.EXPORTED ->
                "FIT file generated. You can share it again any time."
            SyncState.EXPORT_FAILED ->
                "FIT export failed. The ride is still stored on this phone."
            SyncState.EXPORT_READY,
            SyncState.LOCAL_ONLY,
            -> "Your ride stays on this phone until you export it."
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
