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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
open class BleConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    protected open val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    protected open val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    // Mutex to ensure thread-safe access to connections and reconnectionJobs
    // since they're accessed from both public methods and BluetoothGattCallback (Binder thread)
    private val connectionsMutex = Mutex()
    private val connections = mutableMapOf<String, DeviceConnection>()
    private val reconnectionJobs = mutableMapOf<String, Job>()

    /**
     * Connect to a BLE device by its address.
     *
     * @param deviceAddress The MAC address of the device to connect to
     * @return StateFlow tracking this device's connection state
     */
    open suspend fun connect(deviceAddress: String): StateFlow<BleConnectionState> {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            val errorState = MutableStateFlow<BleConnectionState>(
                BleConnectionState.Error("Bluetooth is not available or disabled"),
            )
            return errorState.asStateFlow()
        }

        // Use mutex to safely check/create connection
        connectionsMutex.withLock {
            // Return existing connection state if already managing this device
            connections[deviceAddress]?.let { existing ->
                // If disconnected, re-initiate connection
                if (existing.state.value is BleConnectionState.Disconnected) {
                    connectToDevice(deviceAddress)
                }
                return existing.state.asStateFlow()
            }

            val device = try {
                adapter.getRemoteDevice(deviceAddress)
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
    }

    /**
     * Disconnect from a BLE device and remove it from managed connections.
     *
     * @param deviceAddress The MAC address of the device to disconnect from
     */
    open suspend fun disconnect(deviceAddress: String) {
        cancelReconnection(deviceAddress)
        connectionsMutex.withLock {
            connections[deviceAddress]?.let { connection ->
                connection.gatt?.disconnect()
                connection.gatt?.close()
                connection.gatt = null
                connection.state.value = BleConnectionState.Disconnected
            }
            // Remove from connections map to allow fresh reconnection and free resources
            connections.remove(deviceAddress)
        }
    }

    /**
     * Disconnect from all connected devices and clean up resources.
     */
    suspend fun disconnectAll() {
        val deviceAddresses = connectionsMutex.withLock {
            connections.keys.toList()
        }
        deviceAddresses.forEach { deviceAddress ->
            disconnect(deviceAddress)
        }
        connectionsMutex.withLock {
            reconnectionJobs.values.forEach { it.cancel() }
            reconnectionJobs.clear()
        }
    }

    /**
     * Get the current connection state for a device.
     *
     * @param deviceAddress The MAC address of the device
     * @return The current connection state, or Disconnected if not being managed
     */
    suspend fun getConnectionState(deviceAddress: String): BleConnectionState =
        connectionsMutex.withLock {
            connections[deviceAddress]?.state?.value ?: BleConnectionState.Disconnected
        }

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
            // Close any existing GATT before creating a new one to prevent leaks
            connection.gatt?.close()

            val gatt = device.connectGatt(
                context,
                false, // autoConnect = false for faster initial connection
                connection.gattCallback,
                BluetoothDevice.TRANSPORT_LE,
            )

            if (gatt == null) {
                // connectGatt() can return null if BT stack cannot allocate connection
                Log.e(TAG, "connectGatt returned null for $deviceAddress")
                connection.state.value = BleConnectionState.Error(
                    "Failed to allocate GATT connection",
                )
                scheduleReconnection(deviceAddress, connection.reconnectAttempt)
            } else {
                connection.gatt = gatt
            }
        } catch (e: SecurityException) {
            // Should not happen due to @SuppressLint, but handle defensively
            Log.e(TAG, "Security exception connecting to $deviceAddress", e)
            connection.state.value = BleConnectionState.Error(
                "Permission denied: ${e.message}",
                e,
            )
            scheduleReconnection(deviceAddress, connection.reconnectAttempt)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid argument connecting to $deviceAddress", e)
            connection.state.value = BleConnectionState.Error(
                "Invalid connection parameters: ${e.message}",
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
            connectionsMutex.withLock {
                connectToDevice(deviceAddress)
            }
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
                        // Only treat as successful if status is GATT_SUCCESS
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "Connected to $deviceAddress")
                            reconnectAttempt = 0 // Reset reconnection counter on success
                            cancelReconnection(deviceAddress)
                            state.value = BleConnectionState.Connected

                            // Discover services after connection
                            gatt.discoverServices()
                        } else {
                            Log.w(TAG, "Connection failed for $deviceAddress with status: $status")
                            state.value = BleConnectionState.Error(
                                "Connection failed (status: $status)",
                            )
                            gatt.close()
                            this@DeviceConnection.gatt = null
                            scheduleReconnection(deviceAddress, reconnectAttempt)
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Disconnected from $deviceAddress (status: $status)")

                        // Always close the GATT to free resources
                        gatt.close()
                        this@DeviceConnection.gatt = null

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
