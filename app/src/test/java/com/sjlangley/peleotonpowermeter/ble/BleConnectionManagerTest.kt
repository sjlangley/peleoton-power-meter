package com.sjlangley.peleotonpowermeter.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BleConnectionManagerTest {
    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockBluetoothManager: BluetoothManager

    @Mock
    private lateinit var mockBluetoothAdapter: BluetoothAdapter

    @Mock
    private lateinit var mockBluetoothDevice: BluetoothDevice

    @Mock
    private lateinit var mockBluetoothGatt: BluetoothGatt

    private lateinit var testScope: CoroutineScope
    private lateinit var bleConnectionManager: BleConnectionManager

    private lateinit var closeable: AutoCloseable

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)

        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        `when`(mockContext.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(mockBluetoothManager)
        `when`(mockBluetoothManager.adapter).thenReturn(mockBluetoothAdapter)
        `when`(mockBluetoothAdapter.isEnabled).thenReturn(true)

        bleConnectionManager = BleConnectionManager(mockContext, testScope)
    }

    @After
    fun tearDown() {
        bleConnectionManager.disconnectAll()
        testScope.cancel()
        closeable.close()
    }

    @Test
    fun connect_withBluetoothDisabled_returnsErrorState() = runTest {
        `when`(mockBluetoothAdapter.isEnabled).thenReturn(false)
        val manager = BleConnectionManager(mockContext, testScope)

        val state = manager.connect(VALID_DEVICE_ADDRESS)

        val currentState = state.first()
        assertTrue(currentState is BleConnectionState.Error)
        assertEquals("Bluetooth is not available or disabled", (currentState as BleConnectionState.Error).message)
    }

    @Test
    fun connect_withInvalidAddress_returnsErrorState() = runTest {
        `when`(mockBluetoothAdapter.getRemoteDevice(INVALID_DEVICE_ADDRESS))
            .thenThrow(IllegalArgumentException("Invalid address"))

        val state = bleConnectionManager.connect(INVALID_DEVICE_ADDRESS)

        val currentState = state.first()
        assertTrue(currentState is BleConnectionState.Error)
        assertTrue((currentState as BleConnectionState.Error).message.contains("Invalid device address"))
    }

    @Test
    fun connect_withValidAddress_startsConnecting() = runTest {
        `when`(mockBluetoothAdapter.getRemoteDevice(VALID_DEVICE_ADDRESS)).thenReturn(mockBluetoothDevice)

        val state = bleConnectionManager.connect(VALID_DEVICE_ADDRESS)

        // Initial state should be Disconnected or quickly transition to Connecting
        val currentState = state.value
        assertTrue(currentState is BleConnectionState.Disconnected || currentState is BleConnectionState.Connecting)
    }

    @Test
    fun getConnectionState_forUnmanagedDevice_returnsDisconnected() {
        val state = bleConnectionManager.getConnectionState("00:11:22:33:44:55")

        assertEquals(BleConnectionState.Disconnected, state)
    }

    @Test
    fun disconnect_cleansUpConnection() = runTest {
        `when`(mockBluetoothAdapter.getRemoteDevice(VALID_DEVICE_ADDRESS)).thenReturn(mockBluetoothDevice)

        bleConnectionManager.connect(VALID_DEVICE_ADDRESS)
        bleConnectionManager.disconnect(VALID_DEVICE_ADDRESS)

        val state = bleConnectionManager.getConnectionState(VALID_DEVICE_ADDRESS)
        assertEquals(BleConnectionState.Disconnected, state)
    }

    @Test
    fun disconnectAll_cleansUpAllConnections() = runTest {
        `when`(mockBluetoothAdapter.getRemoteDevice(VALID_DEVICE_ADDRESS)).thenReturn(mockBluetoothDevice)
        `when`(mockBluetoothAdapter.getRemoteDevice(SECOND_DEVICE_ADDRESS)).thenReturn(mockBluetoothDevice)

        bleConnectionManager.connect(VALID_DEVICE_ADDRESS)
        bleConnectionManager.connect(SECOND_DEVICE_ADDRESS)
        bleConnectionManager.disconnectAll()

        assertEquals(BleConnectionState.Disconnected, bleConnectionManager.getConnectionState(VALID_DEVICE_ADDRESS))
        assertEquals(BleConnectionState.Disconnected, bleConnectionManager.getConnectionState(SECOND_DEVICE_ADDRESS))
    }

    @Test
    fun connect_toSameDeviceTwice_returnsSameStateFlow() = runTest {
        `when`(mockBluetoothAdapter.getRemoteDevice(VALID_DEVICE_ADDRESS)).thenReturn(mockBluetoothDevice)

        val state1 = bleConnectionManager.connect(VALID_DEVICE_ADDRESS)
        val state2 = bleConnectionManager.connect(VALID_DEVICE_ADDRESS)

        // Should track the same connection state
        assertEquals(state1.value, state2.value)
    }

    companion object {
        private const val VALID_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF"
        private const val SECOND_DEVICE_ADDRESS = "11:22:33:44:55:66"
        private const val INVALID_DEVICE_ADDRESS = "invalid"
    }
}
