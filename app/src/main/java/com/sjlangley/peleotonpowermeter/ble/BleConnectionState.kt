package com.sjlangley.peleotonpowermeter.ble

/**
 * Represents the connection state of a BLE device.
 */
sealed interface BleConnectionState {
    /**
     * Not connected to the device.
     */
    data object Disconnected : BleConnectionState

    /**
     * Attempting to connect to the device.
     */
    data object Connecting : BleConnectionState

    /**
     * Successfully connected to the device and ready for communication.
     */
    data object Connected : BleConnectionState

    /**
     * Connection failed or encountered an error.
     *
     * @param message Human-readable error description
     * @param cause Optional exception that caused the error
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : BleConnectionState
}
