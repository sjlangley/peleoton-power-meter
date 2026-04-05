package com.sjlangley.peleotonpowermeter.ble

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for Bluetooth SIG Heart Rate Measurement characteristic (0x2A37).
 *
 * Handles the binary format defined in the Heart Rate Service specification,
 * including flags-based field presence detection and proper byte order handling.
 *
 * Format:
 * - Flags (1 byte, uint8)
 * - Heart Rate BPM (1 or 2 bytes, uint8 or uint16, little-endian)
 * - Optional fields based on flags:
 *   - Energy Expended (2 bytes, uint16, little-endian)
 *   - RR-Intervals (1 or more 2-byte uint16 values, little-endian, 1/1024 second resolution)
 */
object HeartRateParser {
    // Flag bit positions (from Bluetooth SIG spec)
    private const val FLAG_HR_VALUE_FORMAT_UINT16 = 0x01  // 0=UINT8, 1=UINT16
    private const val FLAG_SENSOR_CONTACT_SUPPORTED = 0x04
    private const val FLAG_SENSOR_CONTACT_DETECTED = 0x02
    private const val FLAG_ENERGY_EXPENDED_PRESENT = 0x08
    private const val FLAG_RR_INTERVAL_PRESENT = 0x10

    /**
     * Parse Heart Rate Measurement characteristic data.
     *
     * Returns null if data is invalid or cannot be parsed. This is intentional behavior
     * for malformed BLE messages rather than throwing exceptions.
     *
     * @param data Raw bytes from the characteristic value
     * @return Parsed heart rate data, or null if data is invalid
     */
    @Suppress(
        "SwallowedException", // Returning null for parse failures is intentional design
    )
    fun parse(data: ByteArray): HeartRateData? {
        if (data.isEmpty()) {
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // Read flags (1 byte, uint8)
            val flags = buffer.get().toInt() and 0xFF

            // Determine HR value format and read BPM
            val heartRateBpm = if (flags and FLAG_HR_VALUE_FORMAT_UINT16 != 0) {
                // UINT16 format
                buffer.short.toInt() and 0xFFFF
            } else {
                // UINT8 format
                buffer.get().toInt() and 0xFF
            }

            // Parse sensor contact status
            val sensorContactDetected = if (flags and FLAG_SENSOR_CONTACT_SUPPORTED != 0) {
                // Sensor contact is supported, check if detected
                flags and FLAG_SENSOR_CONTACT_DETECTED != 0
            } else {
                // Sensor contact not supported
                null
            }

            // Parse optional energy expended
            val energyExpended = if (flags and FLAG_ENERGY_EXPENDED_PRESENT != 0) {
                buffer.short.toInt() and 0xFFFF
            } else {
                null
            }

            // Parse optional RR-intervals (remaining bytes in pairs)
            val rrIntervals = mutableListOf<Int>()
            if (flags and FLAG_RR_INTERVAL_PRESENT != 0) {
                while (buffer.remaining() >= 2) {
                    val interval = buffer.short.toInt() and 0xFFFF
                    rrIntervals.add(interval)
                }
                // Validate no odd-length payload (malformed if bytes remain)
                if (buffer.remaining() > 0) {
                    return null
                }
            }

            HeartRateData(
                heartRateBpm = heartRateBpm,
                sensorContactDetected = sensorContactDetected,
                energyExpended = energyExpended,
                rrIntervals = rrIntervals.toList(),
            )
        } catch (e: BufferUnderflowException) {
            // Malformed message - not enough bytes for the indicated flags
            null
        } catch (e: IllegalArgumentException) {
            // HeartRateData validation failed (e.g., invalid BPM range)
            null
        }
    }

    /**
     * Check if the data contains energy expended information.
     */
    fun hasEnergyExpended(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        val flags = data[0].toInt() and 0xFF
        return flags and FLAG_ENERGY_EXPENDED_PRESENT != 0
    }

    /**
     * Check if the data contains RR-interval data for HRV analysis.
     */
    fun hasRrIntervals(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        val flags = data[0].toInt() and 0xFF
        return flags and FLAG_RR_INTERVAL_PRESENT != 0
    }

    /**
     * Check if the sensor supports contact detection.
     */
    fun hasSensorContactSupport(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        val flags = data[0].toInt() and 0xFF
        return flags and FLAG_SENSOR_CONTACT_SUPPORTED != 0
    }
}
