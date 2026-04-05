package com.sjlangley.peleotonpowermeter.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for HeartRateParser using realistic heart rate monitor message patterns.
 *
 * Test data represents typical byte sequences from common HR monitors (Polar, Garmin,
 * Wahoo, etc.) following the Bluetooth SIG Heart Rate Service specification.
 * Values are realistic for actual cycling/fitness scenarios.
 *
 * Note: While these tests use realistic patterns based on the Bluetooth SIG spec,
 * they should ideally be validated against actual BLE captures from hardware when available.
 */
class HeartRateParserTest {

    @Test
    fun parse_basicHeartRateUint8_success() {
        // Most common format: UINT8 BPM, no sensor contact, no optional fields
        // Flags: 0x00 (UINT8 format, no contact detection, no optional fields)
        // HR: 75 BPM (resting heart rate)
        val data = byteArrayOf(
            0x00,  // Flags
            0x4B,  // Heart Rate: 75 BPM
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(75, result?.heartRateBpm)
        assertNull(result?.sensorContactDetected)
        assertNull(result?.energyExpended)
        assertTrue(result?.rrIntervals?.isEmpty() ?: false)
        assertFalse(result?.hasEnergyExpended ?: true)
        assertFalse(result?.hasRrIntervals ?: true)
        assertFalse(result?.supportsContactDetection ?: true)
    }

    @Test
    fun parse_withSensorContactDetected_success() {
        // Polar H10 typical format: UINT8 BPM with sensor contact detected
        // Flags: 0x06 (UINT8 format, contact supported + detected)
        // HR: 145 BPM (moderate intensity cycling)
        val data = byteArrayOf(
            0x06,  // Flags: 0000 0110 (contact supported + detected)
            0x91.toByte(),  // Heart Rate: 145 BPM
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(145, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
        assertTrue(result?.supportsContactDetection ?: false)
    }

    @Test
    fun parse_withSensorContactNotDetected_success() {
        // Sensor contact supported but not detected (loose strap)
        // Flags: 0x04 (UINT8 format, contact supported but NOT detected)
        // HR: 0 BPM (typically sent when no contact)
        val data = byteArrayOf(
            0x04,  // Flags: 0000 0100 (contact supported, not detected)
            0x00,  // Heart Rate: 0 BPM (no valid reading)
        )

        val result = HeartRateParser.parse(data)

        // Parser will reject 0 BPM due to validation (must be 1-255)
        assertNull(result)
    }

    @Test
    fun parse_restingHeartRate_success() {
        // Resting heart rate: 55 BPM (well-trained cyclist)
        // Flags: 0x06 (contact detected)
        val data = byteArrayOf(
            0x06,  // Flags: contact detected
            0x37,  // Heart Rate: 55 BPM
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(55, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
    }

    @Test
    fun parse_highIntensityHeartRate_success() {
        // High intensity: 185 BPM (near max for 35-year-old)
        // Flags: 0x06 (contact detected)
        val data = byteArrayOf(
            0x06,  // Flags
            0xB9.toByte(),  // Heart Rate: 185 BPM
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(185, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
    }

    @Test
    fun parse_maxHeartRate_success() {
        // Maximum UINT8 heart rate: 255 BPM (extreme, unlikely but valid)
        // Flags: 0x06 (contact detected)
        val data = byteArrayOf(
            0x06,  // Flags
            0xFF.toByte(),  // Heart Rate: 255 BPM
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(255, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
    }

    @Test
    fun parse_uint16HeartRate_success() {
        // UINT16 format (rare, for HR > 255 BPM which shouldn't happen normally)
        // Flags: 0x01 (UINT16 format)
        // HR: 140 BPM (stored as UINT16)
        val data = byteArrayOf(
            0x01,  // Flags: UINT16 format
            0x8C.toByte(), 0x00,  // Heart Rate: 140 BPM (little-endian)
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(140, result?.heartRateBpm)
        assertNull(result?.sensorContactDetected)
    }

    @Test
    fun parse_withEnergyExpended_success() {
        // Garmin HRM-Dual typical format with energy expended
        // Flags: 0x0E (UINT8 format, contact detected, energy present)
        // HR: 155 BPM
        // Energy: 1250 kJ
        val data = byteArrayOf(
            0x0E,  // Flags: 0000 1110 (contact + energy)
            0x9B.toByte(),  // Heart Rate: 155 BPM
            0xE2.toByte(), 0x04,  // Energy Expended: 1250 kJ (little-endian)
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(155, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
        assertEquals(1250, result?.energyExpended)
        assertTrue(result?.hasEnergyExpended ?: false)
    }

    @Test
    fun parse_withRrIntervals_success() {
        // Polar H10 with RR-intervals for HRV analysis
        // Flags: 0x16 (UINT8 format, contact detected, RR-intervals present)
        // HR: 72 BPM
        // RR-intervals: 3 intervals (833ms, 820ms, 845ms in 1/1024 second units)
        val data = byteArrayOf(
            0x16,  // Flags: 0001 0110 (contact + RR)
            0x48,  // Heart Rate: 72 BPM
            0x41, 0x03,  // RR-interval 1: 833 (1/1024 sec) = ~813ms
            0x34, 0x03,  // RR-interval 2: 820 (1/1024 sec) = ~801ms
            0x4D, 0x03,  // RR-interval 3: 845 (1/1024 sec) = ~825ms
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(72, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
        assertEquals(3, result?.rrIntervals?.size)
        assertEquals(833, result?.rrIntervals?.get(0))
        assertEquals(820, result?.rrIntervals?.get(1))
        assertEquals(845, result?.rrIntervals?.get(2))
        assertTrue(result?.hasRrIntervals ?: false)
    }

    @Test
    fun parse_withSingleRrInterval_success() {
        // HR monitor with single RR-interval
        // Flags: 0x16 (contact + RR)
        // HR: 90 BPM
        // RR-interval: 667 (1/1024 sec) = ~651ms (~92 BPM calculated)
        val data = byteArrayOf(
            0x16,  // Flags
            0x5A,  // Heart Rate: 90 BPM
            0x9B.toByte(), 0x02,  // RR-interval: 667 (little-endian)
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(90, result?.heartRateBpm)
        assertEquals(1, result?.rrIntervals?.size)
        assertEquals(667, result?.rrIntervals?.get(0))
    }

    @Test
    fun parse_fullMessage_success() {
        // Complete message with all optional fields
        // Flags: 0x1E (UINT8 format, contact detected, energy + RR present)
        // HR: 162 BPM (high intensity)
        // Energy: 3500 kJ
        // RR-intervals: 2 intervals
        val data = byteArrayOf(
            0x1E,  // Flags: 0001 1110 (contact + energy + RR)
            0xA2.toByte(),  // Heart Rate: 162 BPM
            0xAC.toByte(), 0x0D,  // Energy: 3500 kJ
            0xD8.toByte(), 0x01,  // RR-interval 1: 472 (~461ms)
            0xE2.toByte(), 0x01,  // RR-interval 2: 482 (~471ms)
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(162, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
        assertEquals(3500, result?.energyExpended)
        assertEquals(2, result?.rrIntervals?.size)
        assertEquals(472, result?.rrIntervals?.get(0))
        assertEquals(482, result?.rrIntervals?.get(1))
        assertTrue(result?.hasEnergyExpended ?: false)
        assertTrue(result?.hasRrIntervals ?: false)
        assertTrue(result?.supportsContactDetection ?: false)
    }

    @Test
    fun parse_wahooTickrFormat_success() {
        // Wahoo TICKR typical format
        // Flags: 0x06 (contact detected, no optional fields)
        // HR: 138 BPM
        val data = byteArrayOf(
            0x06,  // Flags
            0x8A.toByte(),  // Heart Rate: 138 BPM
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(138, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
        assertNull(result?.energyExpended)
        assertTrue(result?.rrIntervals?.isEmpty() ?: false)
    }

    @Test
    fun parse_garmin945BasicFormat_success() {
        // Garmin Forerunner 945 typical format with optical HR sensor
        // Flags: 0x06 (UINT8 format, contact detected, no optional fields)
        // HR: 132 BPM (moderate intensity)
        val data = byteArrayOf(
            0x06,  // Flags: contact detected
            0x84.toByte(),  // Heart Rate: 132 BPM
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(132, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
        assertNull(result?.energyExpended)
        assertTrue(result?.rrIntervals?.isEmpty() ?: false)
        assertTrue(result?.supportsContactDetection ?: false)
    }

    @Test
    fun parse_garmin945WithRrIntervals_success() {
        // Garmin 945 with RR-intervals enabled for HRV tracking
        // Flags: 0x16 (UINT8 format, contact detected, RR-intervals present)
        // HR: 78 BPM (recovery zone)
        // RR-intervals: 2 intervals for HRV
        val data = byteArrayOf(
            0x16,  // Flags: contact + RR
            0x4E,  // Heart Rate: 78 BPM
            0x00, 0x03,  // RR-interval 1: 768 (1/1024 sec) = ~750ms
            0x0C, 0x03,  // RR-interval 2: 780 (1/1024 sec) = ~762ms
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(78, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
        assertEquals(2, result?.rrIntervals?.size)
        assertEquals(768, result?.rrIntervals?.get(0))
        assertEquals(780, result?.rrIntervals?.get(1))
        assertTrue(result?.hasRrIntervals ?: false)
    }

    @Test
    fun parse_garmin945HighIntensity_success() {
        // Garmin 945 during high intensity interval
        // Flags: 0x06 (contact detected)
        // HR: 171 BPM (near threshold)
        val data = byteArrayOf(
            0x06,  // Flags
            0xAB.toByte(),  // Heart Rate: 171 BPM
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(171, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
    }

    @Test
    fun parse_garmin945VariableContactQuality_success() {
        // Garmin 945 optical sensor sequence showing variable readings
        // Simulates transition during arm movement
        // Flags: 0x06 (contact detected)
        // HR: 145 BPM (stable reading during good contact)
        val data = byteArrayOf(
            0x06,  // Flags: contact detected
            0x91.toByte(),  // Heart Rate: 145 BPM
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(145, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
    }

    @Test
    fun parse_garmin945RestingHr_success() {
        // Garmin 945 resting heart rate measurement
        // Flags: 0x06 (contact detected)
        // HR: 48 BPM (very low resting HR for elite athlete)
        val data = byteArrayOf(
            0x06,  // Flags
            0x30,  // Heart Rate: 48 BPM
        )

        val result = HeartRateParser.parse(data)

        assertNotNull(result)
        assertEquals(48, result?.heartRateBpm)
        assertEquals(true, result?.sensorContactDetected)
    }

    @Test
    fun parse_empty_returnsNull() {
        val data = byteArrayOf()

        val result = HeartRateParser.parse(data)

        assertNull(result)
    }

    @Test
    fun parse_truncatedMessage_returnsNull() {
        // Flags indicate UINT16 format but only provides 1 byte of HR data
        val data = byteArrayOf(
            0x01,  // Flags: UINT16 format
            0x8C.toByte(),  // Only 1 byte (needs 2)
        )

        val result = HeartRateParser.parse(data)

        assertNull(result)
    }

    @Test
    fun parse_truncatedEnergyField_returnsNull() {
        // Flags indicate energy present but message is truncated
        val data = byteArrayOf(
            0x0E,  // Flags: energy present
            0x9B.toByte(),  // Heart Rate: 155 BPM
            0xE2.toByte(),  // Only 1 byte of energy (needs 2)
        )

        val result = HeartRateParser.parse(data)

        assertNull(result)
    }

    @Test
    fun parse_invalidHeartRateTooLow_returnsNull() {
        // Heart rate of 0 is invalid (must be 1-255)
        val data = byteArrayOf(
            0x00,  // Flags
            0x00,  // Heart Rate: 0 BPM (invalid)
        )

        val result = HeartRateParser.parse(data)

        assertNull(result)
    }

    @Test
    fun hasEnergyExpended_flagSet_returnsTrue() {
        val data = byteArrayOf(
            0x0E,  // Flags: energy present
            0x9B.toByte(),  // Heart Rate
            0xE2.toByte(), 0x04,  // Energy
        )

        assertTrue(HeartRateParser.hasEnergyExpended(data))
    }

    @Test
    fun hasEnergyExpended_flagNotSet_returnsFalse() {
        val data = byteArrayOf(
            0x06,  // Flags: no energy
            0x9B.toByte(),  // Heart Rate
        )

        assertFalse(HeartRateParser.hasEnergyExpended(data))
    }

    @Test
    fun hasRrIntervals_flagSet_returnsTrue() {
        val data = byteArrayOf(
            0x16,  // Flags: RR-intervals present
            0x48,  // Heart Rate
            0x41, 0x03,  // RR-interval
        )

        assertTrue(HeartRateParser.hasRrIntervals(data))
    }

    @Test
    fun hasRrIntervals_flagNotSet_returnsFalse() {
        val data = byteArrayOf(
            0x06,  // Flags: no RR
            0x48,  // Heart Rate
        )

        assertFalse(HeartRateParser.hasRrIntervals(data))
    }

    @Test
    fun hasSensorContactSupport_flagSet_returnsTrue() {
        val data = byteArrayOf(
            0x06,  // Flags: contact supported + detected
            0x9B.toByte(),  // Heart Rate
        )

        assertTrue(HeartRateParser.hasSensorContactSupport(data))
    }

    @Test
    fun hasSensorContactSupport_flagNotSet_returnsFalse() {
        val data = byteArrayOf(
            0x00,  // Flags: no contact support
            0x9B.toByte(),  // Heart Rate
        )

        assertFalse(HeartRateParser.hasSensorContactSupport(data))
    }

    @Test
    fun hasEnergyExpended_empty_returnsFalse() {
        val data = byteArrayOf()
        assertFalse(HeartRateParser.hasEnergyExpended(data))
    }

    @Test
    fun hasRrIntervals_empty_returnsFalse() {
        val data = byteArrayOf()
        assertFalse(HeartRateParser.hasRrIntervals(data))
    }

    @Test
    fun hasSensorContactSupport_empty_returnsFalse() {
        val data = byteArrayOf()
        assertFalse(HeartRateParser.hasSensorContactSupport(data))
    }
}
