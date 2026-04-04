package com.sjlangley.peleotonpowermeter.data.model

data class SetupDeviceState(
    val label: String,
    val statusLabel: String,
    val state: ConnectionState,
)

data class SetupUiState(
    val devices: List<SetupDeviceState>,
    val overallStatus: String,
    val primaryActionLabel: String,
    val primaryActionEnabled: Boolean,
    val canStartRide: Boolean,
    val secondaryActionLabel: String,
)

data class LiveRideUiState(
    val elapsedLabel: String,
    val powerWatts: Int,
    val cadenceRpm: Int?,
    val heartRateBpm: Int?,
    val zoneLabel: String,
    val zoneProgress: Float,
    val truthStrip: String?,
    val primaryActionLabel: String,
    val secondaryActionLabel: String,
)

data class SummaryUiState(
    val rideLabel: String,
    val averagePowerLabel: String,
    val averageCadenceLabel: String,
    val averageHeartRateLabel: String,
    val asymmetryIntervals: List<AsymmetryInterval>,
    val asymmetryMessage: String,
    val exportLabel: String,
    val resetLabel: String,
)

enum class AppScreen {
    SETUP,
    LIVE,
    SUMMARY,
}

data class AppUiState(
    val currentScreen: AppScreen,
    val setup: SetupUiState,
    val live: LiveRideUiState,
    val summary: SummaryUiState,
)
