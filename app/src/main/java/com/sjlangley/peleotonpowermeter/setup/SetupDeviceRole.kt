package com.sjlangley.peleotonpowermeter.setup

enum class SetupDeviceRole(
    val label: String,
    val waitingLabel: String,
) {
    LEFT_PEDAL(
        label = "Left Pedal",
        waitingLabel = "left pedal",
    ),
    RIGHT_PEDAL(
        label = "Right Pedal",
        waitingLabel = "right pedal",
    ),
    HEART_RATE(
        label = "Heart Rate",
        waitingLabel = "heart-rate monitor",
    ),
}
