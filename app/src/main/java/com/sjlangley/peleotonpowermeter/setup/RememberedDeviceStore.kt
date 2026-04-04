package com.sjlangley.peleotonpowermeter.setup

interface RememberedDeviceStore {
    fun loadRememberedDevices(): RememberedDevices

    fun rememberDevice(
        role: SetupDeviceRole,
        rememberedDevice: RememberedDevice,
    )

    fun clearRememberedDevices()
}
