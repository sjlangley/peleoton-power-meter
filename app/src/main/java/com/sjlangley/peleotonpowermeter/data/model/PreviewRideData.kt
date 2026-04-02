package com.sjlangley.peleotonpowermeter.data.model

object PreviewRideData {
    fun setupState(): SetupUiState =
        SetupUiState(
            devices = listOf(
                SetupDeviceState("Left Pedal", "Connected", ConnectionState.CONNECTED),
                SetupDeviceState("Right Pedal", "Connected", ConnectionState.CONNECTED),
                SetupDeviceState("Heart Rate", "Connected", ConnectionState.CONNECTED),
            ),
            overallStatus = "All sensors ready",
            primaryActionLabel = "Start Demo Ride",
            canStartRide = true,
            secondaryActionLabel = "Simulate Missing HR",
        )

    fun liveRideState(): LiveRideUiState =
        LiveRideUiState(
            elapsedLabel = "18:42",
            powerWatts = 286,
            cadenceRpm = 92,
            heartRateBpm = 154,
            zoneLabel = "Zone 4",
            zoneProgress = 0.62f,
            truthStrip = "Left pedal disconnected. Recording continues. Balance is partial.",
            primaryActionLabel = "Finish Ride",
            secondaryActionLabel = "Restore Sensors",
        )

    fun summaryState(): SummaryUiState =
        SummaryUiState(
            rideLabel = "42:13 indoor ride",
            averagePowerLabel = "186 W",
            averageCadenceLabel = "89 rpm",
            averageHeartRateLabel = "151 bpm",
            // The summary keeps totals first, then teaches from a few notable
            // post-ride asymmetry moments instead of overwhelming the rider.
            asymmetryIntervals = listOf(
                AsymmetryInterval("12:10", "12:55", leftPercent = 46, rightPercent = 54, supported = true),
                AsymmetryInterval("24:40", "25:30", leftPercent = 53, rightPercent = 47, supported = true),
                AsymmetryInterval("31:00", "31:50", leftPercent = 45, rightPercent = 55, supported = true),
            ),
            asymmetryMessage = "Asymmetry is a post-ride insight. Unsupported intervals stay suppressed.",
            exportLabel = "Export FIT",
            resetLabel = "Start Another Demo Ride",
        )

    fun appState(): AppUiState =
        AppUiState(
            currentScreen = AppScreen.SETUP,
            setup = setupState(),
            live = liveRideState(),
            summary = summaryState(),
        )
}
