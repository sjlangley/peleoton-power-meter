package com.sjlangley.peleotonpowermeter.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CyclingPowerParser using realistic Favero Assioma Duo message patterns.
 *
 * Test data represents typical byte sequences from Assioma Duo pedals following the
 * Bluetooth SIG Cycling Power Service specification. Values are realistic for actual
 * cycling scenarios (power: 0-400W, cadence: 60-110 RPM, balance: 45-55%).
 *
 * Note: While these tests use realistic patterns based on the Bluetooth SIG spec and
 * Assioma Duo documentation, they should ideally be validated against actual BLE captures
 * from hardware when available.
 */
class CyclingPowerParserTest {

    @Test
    fun parse_basicPowerOnly_success() {
        // Minimal message with only power (no optional fields)
        // Note: Assioma Duo doesn't typically send this format - it always includes
        // balance and crank data. This tests the parser's ability to handle minimal messages.
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
    fun parse_assiomaMinimal_success() {
        // Realistic Assioma Duo message with balance and crank data (no energy)
        // Flags: 0x0021 (balance + crank data)
        // This is what Assioma may send when energy accumulation is disabled
        // Power: 180W
        // Balance: 51% (slightly right-biased)
        // Crank Revolutions: 450
        // Last Crank Event Time: 9216 (9.0 seconds)
        val data = byteArrayOf(
            0x21, 0x00,  // Flags: 0x0021
            0xB4.toByte(), 0x00,  // Power: 180W
            0x33,  // Balance: 51%
            0xC2.toByte(), 0x01,  // Crank Revolutions: 450
            0x00, 0x24,  // Last Crank Event Time: 9216
        )

        val result = CyclingPowerParser.parse(data)

        assertNotNull(result)
        assertEquals(180, result?.instantaneousPower)
        assertEquals(51, result?.pedalPowerBalance)
        assertEquals(450, result?.crankRevolutions)
        assertEquals(9216, result?.lastCrankEventTime)
        assertNull(result?.accumulatedEnergy)
        assertTrue(result?.hasBalance ?: false)
        assertTrue(result?.hasCadenceData ?: false)
    }

    @Test
    fun parse_powerWithPedalBalance_success() {
        // Message with balance but no cadence
        // Note: Assioma Duo always sends crank data, so this tests parser flexibility
        // Flags: 0x0001 (pedal power balance present)
        // Power: 200W
        // Balance: 50% (perfectly balanced)
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
        // Message with cadence but no balance
        // Note: Assioma Duo always sends balance, so this tests parser flexibility
        // Flags: 0x0020 (crank revolution data present)
        // Power: 180W
        // Crank Revolutions: 1024
        // Last Crank Event Time: 32768 (32 seconds)
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
        // Typical Assioma Duo message with balance, crank data, and accumulated energy
        // Flags: 0x0821 (balance + crank data + accumulated energy)
        // This is the standard format Assioma Duo sends during active pedaling
        // Power: 225W (moderate effort)
        // Balance: 48% (slightly left-biased, typical for many cyclists)
        // Crank Revolutions: 2048 (cumulative since sensor reset)
        // Last Crank Event Time: 45056 (44.0 seconds at 1024 Hz)
        // Accumulated Energy: 1500kJ
        val data = byteArrayOf(
            0x21, 0x08,  // Flags: 0x0821 (little-endian)
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
    fun parse_realisticCadenceCalculation_success() {
        // Two sequential Assioma Duo messages 1 second apart at 90 RPM
        // 90 RPM = 1.5 revolutions per second
        
        // Message 1: t=10.0s, 1000 total revolutions
        val data1 = byteArrayOf(
            0x21, 0x08,  // Flags: balance + crank + energy (Assioma standard)
            0xDC.toByte(), 0x00,  // Power: 220W
            0x31,  // Balance: 49%
            0xE8.toByte(), 0x03,  // Crank Revolutions: 1000
            0x00, 0x28,  // Last Crank Event Time: 10240 (10.0 seconds at 1024 Hz)
            0xC8.toByte(), 0x00,  // Accumulated Energy: 200kJ
        )

        // Message 2: t=11.0s, 1001.5 revolutions (1.5 more = 90 RPM)
        val data2 = byteArrayOf(
            0x21, 0x08,  // Flags: balance + crank + energy
            0xDC.toByte(), 0x00,  // Power: 220W
            0x31,  // Balance: 49%
            0xE9.toByte(), 0x03,  // Crank Revolutions: 1001 (rounded down from 1001.5)
            0x00, 0x2C,  // Last Crank Event Time: 11264 (11.0 seconds at 1024 Hz)
            0xC9.toByte(), 0x00,  // Accumulated Energy: 201kJ
        )

        val result1 = CyclingPowerParser.parse(data1)
        val result2 = CyclingPowerParser.parse(data2)

        assertNotNull(result1)
        assertNotNull(result2)

        // Cadence: 1 revolution / 1.0 seconds * 60 = 60 RPM
        // (Note: Using 1 rev instead of 1.5 due to integer revolution counter)
        val cadence = result2?.calculateCadence(result1)
        assertNotNull(cadence)
        assertTrue("Cadence should be around 60 RPM, got $cadence", 
            cadence!! in 58.0..62.0)
    }

    @Test
    fun parse_realisticHighCadence_success() {
        // Two sequential messages simulating 100 RPM cadence
        // 100 RPM = 1.667 revolutions per second
        // Testing over 3 seconds = 5 revolutions
        
        // Message 1: t=5.0s, 500 total revolutions
        val data1 = byteArrayOf(
            0x21, 0x08,  // Flags: Assioma standard
            0x2C.toByte(), 0x01,  // Power: 300W (higher power at high cadence)
            0x32,  // Balance: 50% (balanced)
            0xF4.toByte(), 0x01,  // Crank Revolutions: 500
            0x00, 0x14,  // Last Crank Event Time: 5120 (5.0 seconds at 1024 Hz)
            0x90.toByte(), 0x01,  // Accumulated Energy: 400kJ
        )

        // Message 2: t=8.0s, 505 revolutions (5 more in 3 seconds)
        val data2 = byteArrayOf(
            0x21, 0x08,  // Flags: Assioma standard
            0x2C.toByte(), 0x01,  // Power: 300W
            0x32,  // Balance: 50%
            0xF9.toByte(), 0x01,  // Crank Revolutions: 505
            0x00, 0x20,  // Last Crank Event Time: 8192 (8.0 seconds at 1024 Hz)
            0x95.toByte(), 0x01,  // Accumulated Energy: 405kJ
        )

        val result1 = CyclingPowerParser.parse(data1)
        val result2 = CyclingPowerParser.parse(data2)

        assertNotNull(result1)
        assertNotNull(result2)

        // Cadence: 5 revolutions / 3.0 seconds * 60 = 100 RPM
        val cadence = result2?.calculateCadence(result1)
        assertNotNull(cadence)
        assertTrue("Cadence should be around 100 RPM, got $cadence", 
            cadence!! in 98.0..102.0)
    }

    @Test
    fun parse_realisticLowCadence_success() {
        // Simulating 70 RPM cadence (lower end of typical range)
        // 70 RPM = 1.167 revolutions per second
        // Testing over 6 seconds = 7 revolutions
        
        val data1 = byteArrayOf(
            0x21, 0x08,  // Flags: Assioma standard
            0x4B.toByte(), 0x01,  // Power: 331W (grinding big gear)
            0x2F,  // Balance: 47% (left-biased when grinding)
            0x64, 0x00,  // Crank Revolutions: 100
            0x00, 0x30,  // Last Crank Event Time: 12288 (12.0 seconds)
            0x58.toByte(), 0x02,  // Accumulated Energy: 600kJ
        )

        val data2 = byteArrayOf(
            0x21, 0x08,  // Flags: Assioma standard
            0x4B.toByte(), 0x01,  // Power: 331W
            0x2F,  // Balance: 47%
            0x6B, 0x00,  // Crank Revolutions: 107
            0x00, 0x48,  // Last Crank Event Time: 18432 (18.0 seconds)
            0x62.toByte(), 0x02,  // Accumulated Energy: 610kJ
        )

        val result1 = CyclingPowerParser.parse(data1)
        val result2 = CyclingPowerParser.parse(data2)

        assertNotNull(result1)
        assertNotNull(result2)

        // Cadence: 7 revolutions / 6.0 seconds * 60 = 70 RPM
        val cadence = result2?.calculateCadence(result1)
        assertNotNull(cadence)
        assertTrue("Cadence should be around 70 RPM, got $cadence", 
            cadence!! in 68.0..72.0)
    }

    @Test
    fun parse_cadenceCalculation_withWraparound_success() {
        // Test 16-bit wraparound in crank revolutions
        // Simulating 90 RPM cadence across a wraparound boundary
        // 90 RPM = 1.5 rev/sec, test over 40 seconds = 60 revolutions
        
        // Message 1: Just before wraparound, t=10922s (65530*1024/1024)
        val data1 = byteArrayOf(
            0x21, 0x08,  // Flags: Assioma standard
            0xE6.toByte(), 0x00,  // Power: 230W
            0x32,  // Balance: 50%
            0xFA.toByte(), 0xFF.toByte(),  // Crank Revolutions: 65530 (near max uint16)
            0xAA.toByte(), 0xFF.toByte(),  // Last Crank Event Time: 65450 (63.916 seconds)
            0xC8.toByte(), 0x00,  // Accumulated Energy: 200kJ
        )

        // Message 2: After wraparound, t=10962s (+40s), 60 more revolutions
        val data2 = byteArrayOf(
            0x21, 0x08,  // Flags: Assioma standard
            0xE6.toByte(), 0x00,  // Power: 230W
            0x32,  // Balance: 50%
            0x3A, 0x00,  // Crank Revolutions: 58 (wrapped: 65530+60-65536=58)
            0xFA.toByte(), 0x9F.toByte(),  // Last Crank Event Time: 40954 (39.994 seconds, wrapped)
            0xD2.toByte(), 0x00,  // Accumulated Energy: 210kJ
        )

        val result1 = CyclingPowerParser.parse(data1)
        val result2 = CyclingPowerParser.parse(data2)

        assertNotNull(result1)
        assertNotNull(result2)

        // Delta revs: (58 + 65536 - 65530) = 64 revolutions
        // Delta time: (40954 + 65536 - 65450) / 1024 = 40.0 seconds
        // Cadence: 64 / 40.0 * 60 = 96 RPM
        val cadence = result2?.calculateCadence(result1)
        assertNotNull(cadence)
        assertTrue("Cadence should be around 96 RPM (wraparound case), got $cadence", 
            cadence!! in 94.0..98.0)
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
