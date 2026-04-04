package com.sjlangley.peleotonpowermeter.setup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation

class SharedPreferencesRememberedDeviceStore(
    context: Context,
) : RememberedDeviceStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun loadRememberedDevices(): RememberedDevices =
        RememberedDevices(
            leftPedal = preferences.rememberedDeviceFor(SetupDeviceRole.LEFT_PEDAL),
            rightPedal = preferences.rememberedDeviceFor(SetupDeviceRole.RIGHT_PEDAL),
            heartRate = preferences.rememberedDeviceFor(SetupDeviceRole.HEART_RATE),
        )

    override fun rememberDevice(
        role: SetupDeviceRole,
        rememberedDevice: RememberedDevice,
    ) {
        val keys = role.keys()
        preferences.edit {
            putInt(keys.associationIdKey, rememberedDevice.associationId)
            putString(keys.deviceIdKey, rememberedDevice.association.deviceId)
            putString(keys.displayNameKey, rememberedDevice.association.displayName)
        }
    }

    override fun clearRememberedDevices() {
        preferences.edit {
            clear()
        }
    }

    private fun SharedPreferences.rememberedDeviceFor(role: SetupDeviceRole): RememberedDevice? {
        val keys = role.keys()
        if (!contains(keys.associationIdKey) || !contains(keys.deviceIdKey) || !contains(keys.displayNameKey)) {
            return null
        }

        val associationId = getInt(keys.associationIdKey, INVALID_ASSOCIATION_ID)
        val deviceId = getString(keys.deviceIdKey, null)
        val displayName = getString(keys.displayNameKey, null)
        if (associationId == INVALID_ASSOCIATION_ID || deviceId.isNullOrBlank() || displayName.isNullOrBlank()) {
            return null
        }

        return RememberedDevice(
            associationId = associationId,
            association =
                DeviceAssociation(
                    deviceId = deviceId,
                    displayName = displayName,
                ),
        )
    }

    private fun SetupDeviceRole.keys(): RoleKeys =
        when (this) {
            SetupDeviceRole.LEFT_PEDAL -> RoleKeys("left_pedal_association_id", "left_pedal_device_id", "left_pedal_display_name")
            SetupDeviceRole.RIGHT_PEDAL -> RoleKeys("right_pedal_association_id", "right_pedal_device_id", "right_pedal_display_name")
            SetupDeviceRole.HEART_RATE -> RoleKeys("heart_rate_association_id", "heart_rate_device_id", "heart_rate_display_name")
        }

    private data class RoleKeys(
        val associationIdKey: String,
        val deviceIdKey: String,
        val displayNameKey: String,
    )

    companion object {
        private const val PREFERENCES_NAME = "remembered-devices"
        private const val INVALID_ASSOCIATION_ID = -1
    }
}
