package com.sjlangley.peleotonpowermeter.recorder

import android.content.Context
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.setup.RememberedDeviceStore

/**
 * Factory for creating the appropriate [RecorderSessionController] based on context and device pairing state.
 *
 * Decision logic:
 * - If [useDemoController] is true → always use [DemoRecorderSessionController]
 * - If paired devices are available → use [BleRecorderSessionController] with device addresses
 * - Otherwise → use [DemoRecorderSessionController] as fallback
 *
 * This allows the app to seamlessly switch between:
 * - Demo mode for emulator testing and development
 * - Real BLE mode when physical devices are paired
 */
object RecorderSessionControllerFactory {
    /**
     * Create a recorder controller based on pairing state and context.
     *
     * @param context Android context for BLE operations
     * @param rideStore Storage for ride sessions and samples
     * @param rememberedDeviceStore Store containing device pairing information
     * @param useDemoController Force demo controller (e.g., for debug actions)
     * @return Appropriate controller implementation
     */
    fun create(
        context: Context,
        rideStore: RideStore,
        rememberedDeviceStore: RememberedDeviceStore,
        useDemoController: Boolean = false,
    ): RecorderSessionController {
        // Force demo controller if explicitly requested (e.g., debug action)
        if (useDemoController) {
            return DemoRecorderSessionController(rideStore)
        }

        // Check if we have all required devices paired
        val rememberedDevices = rememberedDeviceStore.loadRememberedDevices()
        if (!rememberedDevices.isReady()) {
            // Fall back to demo controller if devices aren't paired yet
            return DemoRecorderSessionController(rideStore)
        }

        // Extract device addresses from remembered devices
        // RememberedDevice contains DeviceAssociation which has the device ID (MAC address)
        val leftPedalAddress = rememberedDevices.leftPedal?.association?.deviceId
            ?: return DemoRecorderSessionController(rideStore)
        val rightPedalAddress = rememberedDevices.rightPedal?.association?.deviceId
            ?: return DemoRecorderSessionController(rideStore)
        val heartRateAddress = rememberedDevices.heartRate?.association?.deviceId
            ?: return DemoRecorderSessionController(rideStore)

        // Create BLE controller with paired device addresses
        return BleRecorderSessionController(
            context = context,
            rideStore = rideStore,
            leftPedalAddress = leftPedalAddress,
            rightPedalAddress = rightPedalAddress,
            heartRateAddress = heartRateAddress,
        )
    }
}
