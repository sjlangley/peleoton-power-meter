package com.sjlangley.peleotonpowermeter.setup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesRememberedDeviceStoreTest {
    private lateinit var context: Context
    private lateinit var store: SharedPreferencesRememberedDeviceStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("remembered-devices", Context.MODE_PRIVATE).edit().clear().commit()
        store = SharedPreferencesRememberedDeviceStore(context)
    }

    @Test
    fun rememberDevicePersistsAndLoadsRoleSpecificIdentity() {
        store.rememberDevice(
            SetupDeviceRole.LEFT_PEDAL,
            RememberedDevice(
                associationId = 42,
                association =
                    DeviceAssociation(
                        deviceId = "AA:BB:CC:DD:EE:01",
                        displayName = "Left Assioma",
                    ),
            ),
        )

        val rememberedDevices = store.loadRememberedDevices()

        assertEquals(42, rememberedDevices.leftPedal?.associationId)
        assertEquals("AA:BB:CC:DD:EE:01", rememberedDevices.leftPedal?.association?.deviceId)
        assertEquals("Left Assioma", rememberedDevices.leftPedal?.association?.displayName)
        assertNull(rememberedDevices.rightPedal)
        assertNull(rememberedDevices.heartRate)
    }

    @Test
    fun clearRememberedDevicesRemovesAllStoredIdentity() {
        store.rememberDevice(
            SetupDeviceRole.HEART_RATE,
            RememberedDevice(
                associationId = 9,
                association =
                    DeviceAssociation(
                        deviceId = "AA:BB:CC:DD:EE:09",
                        displayName = "HR Strap",
                    ),
            ),
        )

        store.clearRememberedDevices()

        val rememberedDevices = store.loadRememberedDevices()
        assertFalse(rememberedDevices.hasAnyRememberedDevice())
    }
}
