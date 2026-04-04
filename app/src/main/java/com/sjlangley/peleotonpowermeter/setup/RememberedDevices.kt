package com.sjlangley.peleotonpowermeter.setup

import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation

data class RememberedDevice(
    val associationId: Int,
    val association: DeviceAssociation,
)

data class RememberedDevices(
    val leftPedal: RememberedDevice? = null,
    val rightPedal: RememberedDevice? = null,
    val heartRate: RememberedDevice? = null,
) {
    fun isReady(): Boolean =
        leftPedal != null && rightPedal != null && heartRate != null

    fun hasAnyRememberedDevice(): Boolean =
        leftPedal != null || rightPedal != null || heartRate != null

    fun nextMissingRole(): SetupDeviceRole? =
        when {
            leftPedal == null -> SetupDeviceRole.LEFT_PEDAL
            rightPedal == null -> SetupDeviceRole.RIGHT_PEDAL
            heartRate == null -> SetupDeviceRole.HEART_RATE
            else -> null
        }

    fun rememberedDeviceFor(role: SetupDeviceRole): RememberedDevice? =
        when (role) {
            SetupDeviceRole.LEFT_PEDAL -> leftPedal
            SetupDeviceRole.RIGHT_PEDAL -> rightPedal
            SetupDeviceRole.HEART_RATE -> heartRate
        }

    fun update(
        role: SetupDeviceRole,
        rememberedDevice: RememberedDevice,
    ): RememberedDevices =
        when (role) {
            SetupDeviceRole.LEFT_PEDAL -> copy(leftPedal = rememberedDevice)
            SetupDeviceRole.RIGHT_PEDAL -> copy(rightPedal = rememberedDevice)
            SetupDeviceRole.HEART_RATE -> copy(heartRate = rememberedDevice)
        }

    fun allRememberedDevices(): List<RememberedDevice> =
        listOfNotNull(leftPedal, rightPedal, heartRate)
}
