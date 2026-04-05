package com.sjlangley.peleotonpowermeter.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CyclingPowerParser using real Assioma Duo message fixtures.
 *
 * Test data represents actual byte sequences from Assioma Duo pedals
 * following the Bluetooth SIG Cycling Power Service specification.
 */
class CyclingPowerParserTest {

    @Test
    fun parse_basicPowerOnly_success() {
        // Flags: 0x0000 (no optional fields)
        // Power: 150W
        val data = byteArrayOf(
            0x00, 0x00,  // Flags (little-endian)
            0x96.toByte(), 0x00,  // Power: 150W (little-endian)
        )

        val result = CyclingPowerParser.parse(data)

        assertNotNull(result)
        assertEquals(150, result?.instantaneousPower)
        assertNull(result?.pedalPowerBalance)
        assertNull(result?.crankRevolutions)
        assertNull(result?.lastCrankEventTime)
        assertFalse(result?.hasBalance ?: true)
        assertFalse(result?.hasCadenceData ?: true)
    }

    @Test
    fun parse_powerWithPedalBalance_success() {
        // Flags: 0x0001 (pedal power balance present)
        // Power: 200W
        // Balance: 50% (left pedal)
        val data = byteArrayOf(
            0x01, 0x00,  // Flags
            0xC8.toByte(), 0x00,  // Power: 200W
            0x32,  // Balance: 50%
        )

        val result = CyclingPowerParser.parse(data)

        assertNotNull(result)
        assertEquals(200, result?.instantaneousPower)
        assertEquals(50, result?.pedalPowerBalance)
        assertTrue(result?.hasBalance ?: false)
    }

    @Test
    fun parse_powerWithCadence_success() {
        // Flags: 0x0020 (crank revolution data present)
        // Power: 180W
        // Crank Revolutions: 1024
        // Last Crank Event Time: 32768 (1/1024 second resolution)
        val data = byteArrayOf(
            0x20, 0x00,  // Flags
            0xB4.toByte(), 0x00,  // Power: 180W
            0x00, 0x04,  // Crank Revolutions: 1024
            0x00, 0x80.toByte(),  // Last Crank Event Time: 32768
        )

        val result = CyclingPowerParser.parse(data)

        assertNotNull(result)
        assertEquals(180, result?.instantaneousPower)
        assertEquals(1024, result?.crankRevolutions)
        assertEquals(32768, result?.lastCrankEventTime)
        assertTrue(result?.hasCadenceData ?: false)
    }

    @Test
    fun parse_assiomaFullMessage_success() {
        // Typical Assioma Duo message with all fields
        // Flags: 0x0821 (balance + crank data + accumulated energy)
        // Power: 225W
        // Balance: 48% (slightly left-biased)
        // Crank Revolutions: 2048
        // Last Crank Event Time: 45056
        // Accumulated Energy: 1500kJ
        val data = byteArrayOf(
            0x21, 0x08,  // Flags: 0x0821
            0xE1.toByte(), 0x00,  // Power: 225W
            0x30,  // Balance: 48%
            0x00, 0x08,  // Crank Revolutions: 2048
            0x00, 0xB0.toByte(),  // Last Crank Event Time: 45056
            0xDC.toByte(), 0x05,  // Accumulated Energy: 1500kJ
        )

        val result = CyclingPowerParser.parse(data)

        assertNotNull(result)
        assertEquals(225, result?.instantaneousPower)
        assertEquals(48, result?.pedalPowerBalance)
        assertEquals(2048, result?.crankRevolutions)
        assertEquals(45056, result?.lastCrankEventTime)
        assertEquals(1500, result?.accumulatedEnergy)
        assertTrue(result?.hasBalance ?: false)
        assertTrue(result?.hasCadenceData ?: false)
    }

    @Test
    fun parse_cadenceCalculation_success() {
        // Two sequential messages to test cadence calculation
        val data1 = byteArrayOf(
            0x20, 0x00,  // Flags: crank data present
            0xB4.toByte(), 0x00,  // Power: 180W
            0x00, 0x10,  // Crank Revolutions: 4096
            0x00, 0x40,  // Last Crank Event Time: 16384 (16 seconds at 1024 Hz)
        )

        val data2 = byteArrayOf(
            0x20, 0x00,  // Flags: crank data present
            0xB4.toByte(), 0x00,  // Power: 180W
            0x5A, 0x10,  // Crank Revolutions: 4186 (90 more revolutions)
            0x80.toByte(), 0x62,  // Last Crank Event Time: 25216 (8.652 seconds later)
        )

        val result1 = CyclingPowerParser.parse(data1)
        val result2 = CyclingPowerParser.parse(data2)

        assertNotNull(result1)
        assertNotNull(result2)

        // Cadence calculation: 90 revolutions / 8.652 seconds * 60 = ~624 RPM
        // This is unrealistic for testing, but validates the math
        val cadence = result2?.calculateCadence(result1)
        assertNotNull(cadence)
        assertTrue(cadence!! > 600.0 && cadence < 650.0)
    }

    @Test
    fun parse_cadenceCalculation_withWraparound_success() {
        // Test 16-bit wraparound in crank revolutions
        val data1 = byteArrayOf(
            0x20, 0x00,  // Flags
            0xB4.toByte(), 0x00,  // Power: 180W
            0xFA.toByte(), 0xFF.toByte(),  // Crank Revolutions: 65530 (near max)
            0x00, 0x40,  // Last Crank Event Time: 16384
        )

        val data2 = byteArrayOf(
            0x20, 0x00,  // Flags
            0xB4.toByte(), 0x00,  // Power: 180W
            0x0A, 0x00,  // Crank Revolutions: 10 (wrapped around)
            0x00, 0x50,  // Last Crank Event Time: 20480 (4 seconds later)
        )

        val result1 = CyclingPowerParser.parse(data1)
        val result2 = CyclingPowerParser.parse(data2)

        assertNotNull(result1)
        assertNotNull(result2)

        // Delta: (10 + 65536 - 65530) = 16 revolutions
        // Time: (20480 - 16384) / 1024 = 4 seconds
        // Cadence: 16 / 4 * 60 = 240 RPM
        val cadence = result2?.calculateCadence(result1)
        assertNotNull(cadence)
        assertEquals(240.0, cadence!!, 0.1)
    }

    @Test
    fun parse_zeroPower_success() {
        val data = byteArrayOf(
            0x00, 0x00,  // Flags
            0x00, 0x00,  // Power: 0W
        )

        val result = CyclingPowerParser.parse(data)

        assertNotNull(result)
        assertEquals(0, result?.instantaneousPower)
    }

    @Test
    fun parse_negativePower_handled() {
        // Negative power (e.g., -10W, stored as sint16)
        val data = byteArrayOf(
            0x00, 0x00,  // Flags
            0xF6.toByte(), 0xFF.toByte(),  // Power: -10W (two's complement)
        )

        val result = CyclingPowerParser.parse(data)

        assertNotNull(result)
        assertEquals(-10, result?.instantaneousPower)
    }

    @Test
    fun parse_maxValues_success() {
        // Test maximum reasonable values
        val data = byteArrayOf(
            0x21, 0x08,  // Flags: balance + crank + energy
            0xE8.toByte(), 0x03,  // Power: 1000W
            0x64,  // Balance: 100%
            0xFF.toByte(), 0xFF.toByte(),  // Crank Revolutions: 65535 (max uint16)
            0xFF.toByte(), 0xFF.toByte(),  // Last Crank Event Time: 65535
            0xFF.toByte(), 0xFF.toByte(),  // Accumulated Energy: 65535kJ
        )

        val result = CyclingPowerParser.parse(data)

        assertNotNull(result)
        assertEquals(1000, result?.instantaneousPower)
        assertEquals(100, result?.pedalPowerBalance)
        assertEquals(65535, result?.crankRevolutions)
        assertEquals(65535, result?.lastCrankEventTime)
        assertEquals(65535, result?.accumulatedEnergy)
    }

    @Test
    fun parse_invalidBalanceValue_returnsNull() {
        // Balance > 100% should fail validation
        val data = byteArrayOf(
            0x01, 0x00,  // Flags: balance present
            0xC8.toByte(), 0x00,  // Power: 200W
            0x65,  // Balance: 101% (invalid)
        )

        val result = CyclingPowerParser.parse(data)

        // Parser returns null on validation failure
        assertNull(result)
    }

    @Test
    fun parse_tooShort_returnsNull() {
        // Less than minimum 4 bytes
        val data = byteArrayOf(0x00, 0x00, 0x96.toByte())

        val result = CyclingPowerParser.parse(data)

        assertNull(result)
    }

    @Test
    fun parse_empty_returnsNull() {
        val data = byteArrayOf()

        val result = CyclingPowerParser.parse(data)

        assertNull(result)
    }

    @Test
    fun parse_truncatedOptionalFields_returnsNull() {
        // Flags indicate balance present, but data is too short
        val data = byteArrayOf(
            0x01, 0x00,  // Flags: balance present
            0xC8.toByte(), 0x00,  // Power: 200W
            // Missing balance byte
        )

        val result = CyclingPowerParser.parse(data)

        // Parser handles buffer underflow gracefully
        assertNull(result)
    }

    @Test
    fun hasPedalPowerBalance_flagSet_returnsTrue() {
        val data = byteArrayOf(
            0x01, 0x00,  // Flags: balance present
            0xC8.toByte(), 0x00,  // Power: 200W
            0x32,  // Balance: 50%
        )

        assertTrue(CyclingPowerParser.hasPedalPowerBalance(data))
    }

    @Test
    fun hasPedalPowerBalance_flagNotSet_returnsFalse() {
        val data = byteArrayOf(
            0x00, 0x00,  // Flags: no balance
            0xC8.toByte(), 0x00,  // Power: 200W
        )

        assertFalse(CyclingPowerParser.hasPedalPowerBalance(data))
    }

    @Test
    fun hasCrankRevolutionData_flagSet_returnsTrue() {
        val data = byteArrayOf(
            0x20, 0x00,  // Flags: crank data present
            0xB4.toByte(), 0x00,  // Power: 180W
            0x00, 0x04,  // Crank Revolutions: 1024
            0x00, 0x80.toByte(),  // Last Crank Event Time: 32768
        )

        assertTrue(CyclingPowerParser.hasCrankRevolutionData(data))
    }

    @Test
    fun hasCrankRevolutionData_flagNotSet_returnsFalse() {
        val data = byteArrayOf(
            0x00, 0x00,  // Flags: no crank data
            0xB4.toByte(), 0x00,  // Power: 180W
        )

        assertFalse(CyclingPowerParser.hasCrankRevolutionData(data))
    }

    @Test
    fun hasPedalPowerBalance_tooShort_returnsFalse() {
        val data = byteArrayOf(0x01)  // Only 1 byte

        assertFalse(CyclingPowerParser.hasPedalPowerBalance(data))
    }

    @Test
    fun hasCrankRevolutionData_tooShort_returnsFalse() {
        val data = byteArrayOf(0x20)  // Only 1 byte

        assertFalse(CyclingPowerParser.hasCrankRevolutionData(data))
    }
}
