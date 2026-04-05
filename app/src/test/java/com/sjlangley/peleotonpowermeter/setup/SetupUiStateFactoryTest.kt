package com.sjlangley.peleotonpowermeter.setup

import com.sjlangley.peleotonpowermeter.data.model.ConnectionState
import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupUiStateFactoryTest {
    @Test
    fun emptyRememberedDevicesShowFirstRequiredPairingStep() {
        val state = SetupUiStateFactory.fromRememberedDevices(RememberedDevices())

        assertEquals("Waiting for left pedal", state.overallStatus)
        assertEquals("Pair Left Pedal", state.primaryActionLabel)
        assertTrue(state.primaryActionEnabled)
        assertNull(state.debugActionLabel)
        assertFalse(state.debugActionEnabled)
        assertTrue(state.secondaryActionEnabled)
        assertEquals("Reset Setup", state.secondaryActionLabel)
        assertFalse(state.canStartRide)
        assertEquals("Not paired", state.devices.first().statusLabel)
        assertEquals(ConnectionState.DISCONNECTED, state.devices.first().state)
    }

    @Test
    fun pendingAssociationMarksOnlyThatRoleAsSearching() {
        val state =
            SetupUiStateFactory.fromRememberedDevices(
                rememberedDevices = RememberedDevices(),
                pendingAssociationRole = SetupDeviceRole.LEFT_PEDAL,
            )

        assertEquals("Searching for left pedal", state.overallStatus)
        assertEquals("Searching for left pedal", state.primaryActionLabel)
        assertFalse(state.primaryActionEnabled)
        assertNull(state.debugActionLabel)
        assertFalse(state.debugActionEnabled)
        assertFalse(state.secondaryActionEnabled)
        assertEquals(ConnectionState.SEARCHING, state.devices.first().state)
        assertEquals("Searching", state.devices.first().statusLabel)
        assertFalse(state.canStartRide)
    }

    @Test
    fun debugDemoSensorsActionShowsOnlyWhenEnabledAndSetupIsIncomplete() {
        val state =
            SetupUiStateFactory.fromRememberedDevices(
                rememberedDevices = RememberedDevices(),
                allowDebugDemoSensors = true,
            )

        assertEquals("Use Demo Sensors", state.debugActionLabel)
        assertTrue(state.debugActionEnabled)
        assertFalse(state.canStartRide)
    }

    @Test
    fun readyRememberedDevicesUnlockRideStart() {
        val rememberedDevices =
            RememberedDevices(
                leftPedal = rememberedDevice(1, "Left Assioma", "AA:BB:CC:00:01"),
                rightPedal = rememberedDevice(2, "Right Assioma", "AA:BB:CC:00:02"),
                heartRate = rememberedDevice(3, "HR Strap", "AA:BB:CC:00:03"),
            )

        val state = SetupUiStateFactory.fromRememberedDevices(rememberedDevices)

        assertEquals("All sensors ready", state.overallStatus)
        assertEquals("Start Demo Ride", state.primaryActionLabel)
        assertTrue(state.primaryActionEnabled)
        assertNull(state.debugActionLabel)
        assertFalse(state.debugActionEnabled)
        assertTrue(state.secondaryActionEnabled)
        assertEquals("Change Devices", state.secondaryActionLabel)
        assertTrue(state.canStartRide)
        assertEquals("Left Assioma", state.devices[0].statusLabel)
        assertEquals("Right Assioma", state.devices[1].statusLabel)
        assertEquals("HR Strap", state.devices[2].statusLabel)
    }

    private fun rememberedDevice(
        associationId: Int,
        displayName: String,
        deviceId: String,
    ) = RememberedDevice(associationId, DeviceAssociation(deviceId = deviceId, displayName = displayName))
}
