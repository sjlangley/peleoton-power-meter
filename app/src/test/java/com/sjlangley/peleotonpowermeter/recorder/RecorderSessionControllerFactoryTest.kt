package com.sjlangley.peleotonpowermeter.recorder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.setup.RememberedDevice
import com.sjlangley.peleotonpowermeter.setup.RememberedDeviceStore
import com.sjlangley.peleotonpowermeter.setup.RememberedDevices
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecorderSessionControllerFactoryTest {
    private lateinit var context: Context
    private lateinit var rideStore: RideStore
    private lateinit var rememberedDeviceStore: RememberedDeviceStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        rideStore = mock()
        rememberedDeviceStore = mock()
    }

    @Test
    fun create_withUseDemoControllerTrue_returnsDemoController() {
        // Even with devices paired, force demo mode
        val devices = RememberedDevices(
            leftPedal = RememberedDevice(1, DeviceAssociation("AA:BB:CC:DD:EE:01", "Left")),
            rightPedal = RememberedDevice(2, DeviceAssociation("AA:BB:CC:DD:EE:02", "Right")),
            heartRate = RememberedDevice(3, DeviceAssociation("AA:BB:CC:DD:EE:03", "HR")),
        )
        whenever(rememberedDeviceStore.loadRememberedDevices()).thenReturn(devices)

        val controller = RecorderSessionControllerFactory.create(
            context = context,
            rideStore = rideStore,
            rememberedDeviceStore = rememberedDeviceStore,
            useDemoController = true,
        )

        assertTrue("Should be demo controller", controller is DemoRecorderSessionController)
    }

    @Test
    fun create_withNoDevicesPaired_returnsDemoController() {
        val devices = RememberedDevices() // No devices
        whenever(rememberedDeviceStore.loadRememberedDevices()).thenReturn(devices)

        val controller = RecorderSessionControllerFactory.create(
            context = context,
            rideStore = rideStore,
            rememberedDeviceStore = rememberedDeviceStore,
            useDemoController = false,
        )

        assertTrue("Should be demo controller when no devices paired", controller is DemoRecorderSessionController)
    }

    @Test
    fun create_withOnlyLeftPedalPaired_returnsDemoController() {
        val devices = RememberedDevices(
            leftPedal = RememberedDevice(1, DeviceAssociation("AA:BB:CC:DD:EE:01", "Left")),
        )
        whenever(rememberedDeviceStore.loadRememberedDevices()).thenReturn(devices)

        val controller = RecorderSessionControllerFactory.create(
            context = context,
            rideStore = rideStore,
            rememberedDeviceStore = rememberedDeviceStore,
            useDemoController = false,
        )

        assertTrue("Should be demo controller when devices incomplete", controller is DemoRecorderSessionController)
    }

    @Test
    fun create_withAllDevicesPaired_returnsBleController() {
        val devices = RememberedDevices(
            leftPedal = RememberedDevice(1, DeviceAssociation("AA:BB:CC:DD:EE:01", "Left")),
            rightPedal = RememberedDevice(2, DeviceAssociation("AA:BB:CC:DD:EE:02", "Right")),
            heartRate = RememberedDevice(3, DeviceAssociation("AA:BB:CC:DD:EE:03", "HR")),
        )
        whenever(rememberedDeviceStore.loadRememberedDevices()).thenReturn(devices)

        val controller = RecorderSessionControllerFactory.create(
            context = context,
            rideStore = rideStore,
            rememberedDeviceStore = rememberedDeviceStore,
            useDemoController = false,
        )

        assertTrue("Should be BLE controller when all devices paired", controller is BleRecorderSessionController)
    }

    @Test
    fun create_withAllDevicesPairedButDemoForced_returnsDemoController() {
        val devices = RememberedDevices(
            leftPedal = RememberedDevice(1, DeviceAssociation("AA:BB:CC:DD:EE:01", "Left")),
            rightPedal = RememberedDevice(2, DeviceAssociation("AA:BB:CC:DD:EE:02", "Right")),
            heartRate = RememberedDevice(3, DeviceAssociation("AA:BB:CC:DD:EE:03", "HR")),
        )
        whenever(rememberedDeviceStore.loadRememberedDevices()).thenReturn(devices)

        val controller = RecorderSessionControllerFactory.create(
            context = context,
            rideStore = rideStore,
            rememberedDeviceStore = rememberedDeviceStore,
            useDemoController = true,
        )

        assertTrue("Should be demo controller when forced", controller is DemoRecorderSessionController)
    }
}
