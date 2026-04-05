package com.sjlangley.peleotonpowermeter.ble

/**
 * Parsed data from Bluetooth SIG Heart Rate Measurement characteristic (0x2A37).
 *
 * Represents instantaneous heart rate measurements and optional fields like sensor
 * contact status, energy expended, and RR-intervals for heart rate variability.
 *
 * @property heartRateBpm Current heart rate in beats per minute (BPM)
 * @property sensorContactDetected True if the heart rate sensor is properly in contact
 *                                  with the skin and detecting a heart rate. Null if the
 *                                  sensor does not support contact detection. False if
 *                                  the sensor supports detection but contact is not detected.
 * @property energyExpended Cumulative energy expended in kilojoules since sensor reset.
 *                          Null if not present in the measurement.
 * @property rrIntervals List of RR-intervals in milliseconds (time between consecutive
 *                       heartbeats) for heart rate variability analysis. Empty list if
 *                       not present. Each value represents 1/1024 second resolution.
 */
data class HeartRateData(
    val heartRateBpm: Int,
    val sensorContactDetected: Boolean? = null,
    val energyExpended: Int? = null,
    val rrIntervals: List<Int> = emptyList(),
) {
    init {
        require(heartRateBpm in 1..255) { "Heart rate must be 1-255 BPM" }
        energyExpended?.let {
            require(it >= 0) { "Energy expended must be non-negative" }
        }
        rrIntervals.forEach { interval ->
            require(interval > 0) { "RR-intervals must be positive" }
        }
    }

    /**
     * Returns true if this measurement includes energy expended data.
     */
    val hasEnergyExpended: Boolean
        get() = energyExpended != null

    /**
     * Returns true if this measurement includes RR-interval data for HRV analysis.
     */
    val hasRrIntervals: Boolean
        get() = rrIntervals.isNotEmpty()

    /**
     * Returns true if the sensor supports contact detection.
     */
    val supportsContactDetection: Boolean
        get() = sensorContactDetected != null
}
