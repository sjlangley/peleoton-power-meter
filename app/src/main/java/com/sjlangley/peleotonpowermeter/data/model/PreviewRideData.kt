package com.sjlangley.peleotonpowermeter.data.model

object PreviewRideData {
    fun setupState(): SetupUiState =
        SetupUiState(
            devices = listOf(
                SetupDeviceState("Left Pedal", "Not paired", ConnectionState.DISCONNECTED),
                SetupDeviceState("Right Pedal", "Not paired", ConnectionState.DISCONNECTED),
                SetupDeviceState("Heart Rate", "Not paired", ConnectionState.DISCONNECTED),
            ),
            overallStatus = "Waiting for left pedal",
            primaryActionLabel = "Pair Left Pedal",
            canStartRide = false,
            secondaryActionLabel = "Reset Setup",
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
            exportLabel = "Share Demo Summary",
            resetLabel = "Start Another Demo Ride",
        )

    fun demoRideSamples(includePedalDropout: Boolean = false): List<RideSample> =
        buildList {
            addAll(
                sampleSegment(
                    startSecond = 0,
                    durationSeconds = 40,
                    leftPowerWatts = 54,
                    rightPowerWatts = 46,
                    cadenceRpm = 89,
                    heartRateBpm = 145,
                ),
            )
            addAll(
                sampleSegment(
                    startSecond = 40,
                    durationSeconds = 30,
                    leftPowerWatts = 50,
                    rightPowerWatts = 50,
                    cadenceRpm = 90,
                    heartRateBpm = 146,
                ),
            )
            addAll(
                if (includePedalDropout) {
                    sampleSegment(
                        startSecond = 70,
                        durationSeconds = 30,
                        leftPowerWatts = null,
                        rightPowerWatts = 53,
                        cadenceRpm = 88,
                        heartRateBpm = 147,
                        leftConnected = false,
                    )
                } else {
                    sampleSegment(
                        startSecond = 70,
                        durationSeconds = 30,
                        leftPowerWatts = 47,
                        rightPowerWatts = 53,
                        cadenceRpm = 91,
                        heartRateBpm = 147,
                    )
                },
            )
            addAll(
                sampleSegment(
                    startSecond = 100,
                    durationSeconds = 30,
                    leftPowerWatts = 50,
                    rightPowerWatts = 50,
                    cadenceRpm = 90,
                    heartRateBpm = 148,
                ),
            )
            addAll(
                sampleSegment(
                    startSecond = 130,
                    durationSeconds = 30,
                    leftPowerWatts = 44,
                    rightPowerWatts = 56,
                    cadenceRpm = 92,
                    heartRateBpm = 149,
                ),
            )
        }

    fun initialLiveSamples(includePedalDropout: Boolean = false): List<RideSample> =
        demoRideSamples(includePedalDropout = includePedalDropout).take(INITIAL_PERSISTED_SAMPLE_COUNT)

    fun demoRideSession(rideId: String): RideSession =
        RideSession(
            rideId = rideId,
            startedAtEpochSeconds = 0L,
            endedAtEpochSeconds = null,
            ftpWatts = 250,
            pedalPair =
                PedalPair(
                    left = DeviceAssociation(deviceId = "assioma-left", displayName = "Assioma Left"),
                    right = DeviceAssociation(deviceId = "assioma-right", displayName = "Assioma Right"),
                ),
            heartRateSource =
                HeartRateSource(
                    source = DeviceAssociation(deviceId = "heart-rate", displayName = "Heart Rate"),
                ),
            syncState = SyncState.LOCAL_ONLY,
            interruptionDetected = false,
        )

    fun appState(): AppUiState =
        AppUiState(
            currentScreen = AppScreen.SETUP,
            setup = setupState(),
            live = liveRideState(),
            summary = summaryState(),
        )

    private fun sampleSegment(
        startSecond: Long,
        durationSeconds: Int,
        leftPowerWatts: Int?,
        rightPowerWatts: Int?,
        cadenceRpm: Int?,
        heartRateBpm: Int?,
        leftConnected: Boolean = true,
        rightConnected: Boolean = true,
    ): List<RideSample> =
        (0 until durationSeconds).map { offset ->
            RideSample(
                timestampEpochSeconds = startSecond + offset,
                leftPowerWatts = leftPowerWatts.takeIf { leftConnected },
                rightPowerWatts = rightPowerWatts.takeIf { rightConnected },
                totalPowerWatts = listOfNotNull(leftPowerWatts, rightPowerWatts).sum(),
                cadenceRpm = cadenceRpm,
                heartRateBpm = heartRateBpm,
                zoneIndex = 3,
                leftConnected = leftConnected,
                rightConnected = rightConnected,
                heartRateConnected = heartRateBpm != null,
            )
        }

    private const val INITIAL_PERSISTED_SAMPLE_COUNT = 12
}
