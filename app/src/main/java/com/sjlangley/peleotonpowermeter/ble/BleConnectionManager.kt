package com.sjlangley.peleotonpowermeter.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages Bluetooth Low Energy connections to devices.
 *
 * Handles connection lifecycle including connecting, disconnecting, and reconnecting
 * with exponential backoff. Supports multiple simultaneous device connections.
 *
 * Note: This class assumes BLUETOOTH_CONNECT permission is already granted.
 * Permission checking should occur before creating this manager.
 *
 * @param context Application context for accessing Bluetooth services
 * @param scope Coroutine scope for managing reconnection attempts
 */
@SuppressLint("MissingPermission")
class BleConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val connections = mutableMapOf<String, DeviceConnection>()
    private val reconnectionJobs = mutableMapOf<String, Job>()

    /**
     * Connect to a BLE device by its address.
     *
     * @param deviceAddress The MAC address of the device to connect to
     * @return StateFlow tracking this device's connection state
     */
    fun connect(deviceAddress: String): StateFlow<BleConnectionState> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            val errorState = MutableStateFlow<BleConnectionState>(
                BleConnectionState.Error("Bluetooth is not available or disabled"),
            )
            return errorState.asStateFlow()
        }

        // Return existing connection state if already managing this device
        connections[deviceAddress]?.let { return it.state.asStateFlow() }

        val device = try {
            bluetoothAdapter.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            val errorState = MutableStateFlow<BleConnectionState>(
                BleConnectionState.Error("Invalid device address: $deviceAddress", e),
            )
            return errorState.asStateFlow()
        }

        val deviceConnection = DeviceConnection(device)
        connections[deviceAddress] = deviceConnection

        // Initiate connection
        connectToDevice(deviceAddress)

        return deviceConnection.state.asStateFlow()
    }

    /**
     * Disconnect from a BLE device.
     *
     * @param deviceAddress The MAC address of the device to disconnect from
     */
    fun disconnect(deviceAddress: String) {
        cancelReconnection(deviceAddress)
        connections[deviceAddress]?.let { connection ->
            connection.gatt?.disconnect()
            connection.gatt?.close()
            connection.gatt = null
            connection.state.value = BleConnectionState.Disconnected
        }
    }

    /**
     * Disconnect from all connected devices and clean up resources.
     */
    fun disconnectAll() {
        connections.keys.toList().forEach { deviceAddress ->
            disconnect(deviceAddress)
        }
        connections.clear()
        reconnectionJobs.values.forEach { it.cancel() }
        reconnectionJobs.clear()
    }

    /**
     * Get the current connection state for a device.
     *
     * @param deviceAddress The MAC address of the device
     * @return The current connection state, or Disconnected if not being managed
     */
    fun getConnectionState(deviceAddress: String): BleConnectionState =
        connections[deviceAddress]?.state?.value ?: BleConnectionState.Disconnected

    private fun connectToDevice(deviceAddress: String) {
        val connection = connections[deviceAddress] ?: return
        val device = connection.device

        if (connection.state.value is BleConnectionState.Connecting) {
            Log.d(TAG, "Already connecting to $deviceAddress")
            return
        }

        connection.state.value = BleConnectionState.Connecting
        Log.d(TAG, "Connecting to device: $deviceAddress")

        try {
            connection.gatt = device.connectGatt(
                context,
                false, // autoConnect = false for faster initial connection
                connection.gattCallback,
                BluetoothDevice.TRANSPORT_LE,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to $deviceAddress", e)
            connection.state.value = BleConnectionState.Error(
                "Connection attempt failed: ${e.message}",
                e,
            )
            scheduleReconnection(deviceAddress, connection.reconnectAttempt)
        }
    }

    private fun scheduleReconnection(deviceAddress: String, attemptNumber: Int) {
        cancelReconnection(deviceAddress)

        val connection = connections[deviceAddress] ?: return
        connection.reconnectAttempt = attemptNumber + 1

        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, max 32s
        val delayMs = min(
            INITIAL_RECONNECT_DELAY_MS * (2.0.pow(attemptNumber)).toLong(),
            MAX_RECONNECT_DELAY_MS,
        )

        Log.d(TAG, "Scheduling reconnection to $deviceAddress in ${delayMs}ms (attempt ${attemptNumber + 1})")

        val job = scope.launch {
            delay(delayMs)
            Log.d(TAG, "Attempting reconnection to $deviceAddress")
            connectToDevice(deviceAddress)
        }

        reconnectionJobs[deviceAddress] = job
    }

    private fun cancelReconnection(deviceAddress: String) {
        reconnectionJobs[deviceAddress]?.cancel()
        reconnectionJobs.remove(deviceAddress)
    }

    private inner class DeviceConnection(
        val device: BluetoothDevice,
    ) {
        val state = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
        var gatt: BluetoothGatt? = null
        var reconnectAttempt = 0

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val deviceAddress = device.address

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Connected to $deviceAddress")
                        reconnectAttempt = 0 // Reset reconnection counter on success
                        cancelReconnection(deviceAddress)
                        state.value = BleConnectionState.Connected

                        // Discover services after connection
                        gatt.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Disconnected from $deviceAddress (status: $status)")

                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            // Intentional disconnect
                            state.value = BleConnectionState.Disconnected
                        } else {
                            // Unexpected disconnect - attempt reconnection
                            state.value = BleConnectionState.Error(
                                "Connection lost (status: $status)",
                            )
                            scheduleReconnection(deviceAddress, reconnectAttempt)
                        }
                    }

                    BluetoothProfile.STATE_CONNECTING -> {
                        state.value = BleConnectionState.Connecting
                    }

                    BluetoothProfile.STATE_DISCONNECTING -> {
                        // Keep current state during disconnection
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Services discovered for ${device.address}: ${gatt.services.size} services")
                } else {
                    Log.w(TAG, "Service discovery failed for ${device.address} with status: $status")
                }
            }
        }
    }

    companion object {
        private const val TAG = "BleConnectionManager"
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 32000L
    }
}
