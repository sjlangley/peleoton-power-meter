package com.sjlangley.peleotonpowermeter.setup

import com.sjlangley.peleotonpowermeter.data.model.ConnectionState
import com.sjlangley.peleotonpowermeter.data.model.SetupDeviceState
import com.sjlangley.peleotonpowermeter.data.model.SetupUiState

object SetupUiStateFactory {
    fun fromRememberedDevices(
        rememberedDevices: RememberedDevices,
        pendingAssociationRole: SetupDeviceRole? = null,
    ): SetupUiState {
        val nextMissingRole = rememberedDevices.nextMissingRole()

        return SetupUiState(
            devices =
                listOf(
                    deviceState(SetupDeviceRole.LEFT_PEDAL, rememberedDevices.leftPedal, pendingAssociationRole),
                    deviceState(SetupDeviceRole.RIGHT_PEDAL, rememberedDevices.rightPedal, pendingAssociationRole),
                    deviceState(SetupDeviceRole.HEART_RATE, rememberedDevices.heartRate, pendingAssociationRole),
                ),
            overallStatus =
                when {
                    pendingAssociationRole != null -> "Searching for ${pendingAssociationRole.waitingLabel}"
                    nextMissingRole != null -> "Waiting for ${nextMissingRole.waitingLabel}"
                    else -> "All sensors ready"
                },
            primaryActionLabel =
                when {
                    pendingAssociationRole != null -> "Searching for ${pendingAssociationRole.waitingLabel}"
                    nextMissingRole != null -> "Pair ${nextMissingRole.label}"
                    else -> "Start Demo Ride"
                },
            primaryActionEnabled = pendingAssociationRole == null,
            secondaryActionEnabled = pendingAssociationRole == null,
            canStartRide = rememberedDevices.isReady() && pendingAssociationRole == null,
            secondaryActionLabel =
                if (rememberedDevices.hasAnyRememberedDevice()) {
                    "Change Devices"
                } else {
                    "Reset Setup"
                },
        )
    }

    private fun deviceState(
        role: SetupDeviceRole,
        rememberedDevice: RememberedDevice?,
        pendingAssociationRole: SetupDeviceRole?,
    ): SetupDeviceState =
        when {
            role == pendingAssociationRole ->
                SetupDeviceState(
                    label = role.label,
                    statusLabel = "Searching",
                    state = ConnectionState.SEARCHING,
                )

            rememberedDevice != null ->
                SetupDeviceState(
                    label = role.label,
                    statusLabel = rememberedDevice.association.displayName,
                    state = ConnectionState.CONNECTED,
                )

            else ->
                SetupDeviceState(
                    label = role.label,
                    statusLabel = "Not paired",
                    state = ConnectionState.DISCONNECTED,
                )
        }
}
