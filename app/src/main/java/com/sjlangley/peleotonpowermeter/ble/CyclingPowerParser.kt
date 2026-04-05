package com.sjlangley.peleotonpowermeter.ble

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for Bluetooth SIG Cycling Power Measurement characteristic (0x2A63).
 *
 * Handles the binary format defined in the Cycling Power Service specification,
 * including flags-based field presence detection and proper byte order handling.
 *
 * Format:
 * - Flags (2 bytes, uint16, little-endian)
 * - Instantaneous Power (2 bytes, sint16, little-endian) - always present
 * - Optional fields based on flags:
 *   - Pedal Power Balance (1 byte, uint8)
 *   - Accumulated Torque (2 bytes, uint16, little-endian)
 *   - Cumulative Wheel Revolutions (4 bytes, uint32, little-endian)
 *   - Last Wheel Event Time (2 bytes, uint16, little-endian)
 *   - Cumulative Crank Revolutions (2 bytes, uint16, little-endian)
 *   - Last Crank Event Time (2 bytes, uint16, little-endian)
 */
object CyclingPowerParser {
    // Flag bit positions (from Bluetooth SIG spec)
    private const val FLAG_PEDAL_POWER_BALANCE_PRESENT = 0x0001
    private const val FLAG_ACCUMULATED_TORQUE_PRESENT = 0x0004
    private const val FLAG_WHEEL_REVOLUTION_DATA_PRESENT = 0x0010
    private const val FLAG_CRANK_REVOLUTION_DATA_PRESENT = 0x0020
    private const val FLAG_EXTREME_FORCE_MAGNITUDES_PRESENT = 0x0040
    private const val FLAG_EXTREME_TORQUE_MAGNITUDES_PRESENT = 0x0080
    private const val FLAG_EXTREME_ANGLES_PRESENT = 0x0100
    private const val FLAG_TOP_DEAD_SPOT_ANGLE_PRESENT = 0x0200
    private const val FLAG_BOTTOM_DEAD_SPOT_ANGLE_PRESENT = 0x0400
    private const val FLAG_ACCUMULATED_ENERGY_PRESENT = 0x0800

    /**
     * Parse Cycling Power Measurement characteristic data.
     *
     * Returns null if data is invalid or cannot be parsed. This is intentional behavior
     * for malformed BLE messages rather than throwing exceptions.
     *
     * @param data Raw bytes from the characteristic value
     * @return Parsed cycling power data, or null if data is invalid
     */
    @Suppress(
        "CyclomaticComplexMethod", // Complexity follows Bluetooth SIG spec field order
        "SwallowedException", // Returning null for parse failures is intentional design
    )
    fun parse(data: ByteArray): CyclingPowerData? {
        if (data.size < 4) {
            // Minimum: 2 bytes flags + 2 bytes instantaneous power
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // Read flags (2 bytes, uint16)
            val flags = buffer.short.toInt() and 0xFFFF

            // Read instantaneous power (2 bytes, sint16) - always present
            val instantaneousPower = buffer.short.toInt()

            // Read optional fields based on flags
            var pedalPowerBalance: Int? = null
            var accumulatedTorque: Int? = null
            var accumulatedEnergy: Int? = null
            var crankRevolutions: Int? = null
            var lastCrankEventTime: Int? = null

            // Pedal Power Balance (1 byte, uint8)
            if (flags and FLAG_PEDAL_POWER_BALANCE_PRESENT != 0) {
                pedalPowerBalance = buffer.get().toInt() and 0xFF
            }

            // Accumulated Torque (2 bytes, uint16)
            if (flags and FLAG_ACCUMULATED_TORQUE_PRESENT != 0) {
                accumulatedTorque = buffer.short.toInt() and 0xFFFF
            }

            // Skip Wheel Revolution Data if present (not needed for Assioma)
            if (flags and FLAG_WHEEL_REVOLUTION_DATA_PRESENT != 0) {
                buffer.int  // Cumulative Wheel Revolutions (4 bytes)
                buffer.short  // Last Wheel Event Time (2 bytes)
            }

            // Crank Revolution Data (2 + 2 bytes)
            if (flags and FLAG_CRANK_REVOLUTION_DATA_PRESENT != 0) {
                crankRevolutions = buffer.short.toInt() and 0xFFFF
                lastCrankEventTime = buffer.short.toInt() and 0xFFFF
            }

            // Skip Extreme Force/Torque Magnitudes if present
            if (flags and FLAG_EXTREME_FORCE_MAGNITUDES_PRESENT != 0) {
                buffer.short  // Maximum Force Magnitude
                buffer.short  // Minimum Force Magnitude
            }

            if (flags and FLAG_EXTREME_TORQUE_MAGNITUDES_PRESENT != 0) {
                buffer.short  // Maximum Torque Magnitude
                buffer.short  // Minimum Torque Magnitude
            }

            // Skip Extreme Angles if present
            if (flags and FLAG_EXTREME_ANGLES_PRESENT != 0) {
                buffer.get()  // Maximum Angle (12 bits)
                buffer.get()
                buffer.get()  // Minimum Angle (12 bits)
            }

            // Skip Top/Bottom Dead Spot Angles
            if (flags and FLAG_TOP_DEAD_SPOT_ANGLE_PRESENT != 0) {
                buffer.short  // Top Dead Spot Angle
            }

            if (flags and FLAG_BOTTOM_DEAD_SPOT_ANGLE_PRESENT != 0) {
                buffer.short  // Bottom Dead Spot Angle
            }

            // Accumulated Energy (2 bytes, uint16)
            if (flags and FLAG_ACCUMULATED_ENERGY_PRESENT != 0) {
                accumulatedEnergy = buffer.short.toInt() and 0xFFFF
            }

            CyclingPowerData(
                instantaneousPower = instantaneousPower,
                pedalPowerBalance = pedalPowerBalance,
                accumulatedEnergy = accumulatedEnergy,
                accumulatedTorque = accumulatedTorque,
                crankRevolutions = crankRevolutions,
                lastCrankEventTime = lastCrankEventTime,
            )
        } catch (e: BufferUnderflowException) {
            // Malformed message - not enough bytes for the indicated flags
            null
        } catch (e: IllegalArgumentException) {
            // CyclingPowerData validation failed (e.g., invalid balance percentage)
            null
        }
    }

    /**
     * Check if the data contains pedal power balance information.
     */
    fun hasPedalPowerBalance(data: ByteArray): Boolean {
        if (data.size < 2) return false
        val flags = ByteBuffer.wrap(data, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        return flags and FLAG_PEDAL_POWER_BALANCE_PRESENT != 0
    }

    /**
     * Check if the data contains crank revolution data for cadence calculation.
     */
    fun hasCrankRevolutionData(data: ByteArray): Boolean {
        if (data.size < 2) return false
        val flags = ByteBuffer.wrap(data, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        return flags and FLAG_CRANK_REVOLUTION_DATA_PRESENT != 0
    }
}
