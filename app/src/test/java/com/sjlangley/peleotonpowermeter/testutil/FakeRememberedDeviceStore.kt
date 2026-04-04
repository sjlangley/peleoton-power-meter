package com.sjlangley.peleotonpowermeter.testutil

import com.sjlangley.peleotonpowermeter.setup.RememberedDevice
import com.sjlangley.peleotonpowermeter.setup.RememberedDeviceStore
import com.sjlangley.peleotonpowermeter.setup.RememberedDevices
import com.sjlangley.peleotonpowermeter.setup.SetupDeviceRole

class FakeRememberedDeviceStore(
    initialDevices: RememberedDevices = RememberedDevices(),
) : RememberedDeviceStore {
    private var rememberedDevices: RememberedDevices = initialDevices

    override fun loadRememberedDevices(): RememberedDevices = rememberedDevices

    override fun rememberDevice(
        role: SetupDeviceRole,
        rememberedDevice: RememberedDevice,
    ) {
        rememberedDevices = rememberedDevices.update(role, rememberedDevice)
    }

    override fun clearRememberedDevices() {
        rememberedDevices = RememberedDevices()
    }
}
