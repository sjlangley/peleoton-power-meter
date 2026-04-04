package com.sjlangley.peleotonpowermeter.setup

import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RememberedDevicesTest {
    @Test
    fun helperMethodsTrackReadinessAndRoleLookup() {
        val left = rememberedDevice(1, "Left Assioma", "left-id")
        val right = rememberedDevice(2, "Right Assioma", "right-id")
        val heartRate = rememberedDevice(3, "HR Strap", "hr-id")

        val empty = RememberedDevices()
        assertFalse(empty.hasAnyRememberedDevice())
        assertFalse(empty.isReady())
        assertEquals(SetupDeviceRole.LEFT_PEDAL, empty.nextMissingRole())

        val partial = empty.update(SetupDeviceRole.LEFT_PEDAL, left)
        assertTrue(partial.hasAnyRememberedDevice())
        assertFalse(partial.isReady())
        assertEquals(left, partial.rememberedDeviceFor(SetupDeviceRole.LEFT_PEDAL))
        assertEquals(SetupDeviceRole.RIGHT_PEDAL, partial.nextMissingRole())

        val ready =
            partial
                .update(SetupDeviceRole.RIGHT_PEDAL, right)
                .update(SetupDeviceRole.HEART_RATE, heartRate)

        assertTrue(ready.isReady())
        assertEquals(listOf(left, right, heartRate), ready.allRememberedDevices())
        assertEquals(null, ready.nextMissingRole())
    }

    private fun rememberedDevice(
        associationId: Int,
        displayName: String,
        deviceId: String,
    ) = RememberedDevice(associationId, DeviceAssociation(deviceId = deviceId, displayName = displayName))
}
