package com.sjlangley.peleotonpowermeter.ble

/**
 * Parsed data from Bluetooth SIG Cycling Power Measurement characteristic (0x2A63).
 *
 * Represents instantaneous power measurements and optional fields like pedal balance,
 * accumulated energy, torque, and crank/wheel revolution data.
 *
 * @property instantaneousPower Current power output in watts
 * @property pedalPowerBalance Left/right power distribution percentage (0-100), where
 *                             values 0-50 represent left percentage, 50-100 represent right percentage.
 *                             Null if not present in the measurement.
 * @property accumulatedEnergy Cumulative energy in kilojoules since sensor reset. Null if not present.
 * @property accumulatedTorque Cumulative torque in newton-meters. Null if not present.
 * @property crankRevolutions Cumulative crank revolutions since sensor reset. Used for cadence calculation.
 * @property lastCrankEventTime Timestamp of last crank event in 1/1024 second resolution.
 *                              Used with crankRevolutions to calculate cadence.
 */
data class CyclingPowerData(
    val instantaneousPower: Int,
    val pedalPowerBalance: Int? = null,
    val accumulatedEnergy: Int? = null,
    val accumulatedTorque: Int? = null,
    val crankRevolutions: Int? = null,
    val lastCrankEventTime: Int? = null,
) {
    init {
        // Note: instantaneousPower can be negative per Bluetooth SIG spec (sint16)
        // Negative values may occur during coasting or descending
        pedalPowerBalance?.let {
            require(it in 0..100) { "Pedal power balance must be 0-100" }
        }
    }

    /**
     * Calculate cadence in RPM from crank revolution data.
     *
     * Requires both crankRevolutions and lastCrankEventTime from current and previous measurements.
     * Uses the difference in revolutions and time to calculate instantaneous cadence.
     *
     * @param previousData Previous measurement to calculate delta from
     * @return Cadence in RPM, or null if insufficient data
     */
    fun calculateCadence(previousData: CyclingPowerData?): Double? {
        if (crankRevolutions == null || lastCrankEventTime == null) return null
        if (previousData?.crankRevolutions == null || previousData.lastCrankEventTime == null) return null

        val revDelta = crankRevolutions - previousData.crankRevolutions
        val timeDelta = lastCrankEventTime - previousData.lastCrankEventTime

        // Handle wraparound for 16-bit values
        val actualRevDelta = if (revDelta < 0) revDelta + 65536 else revDelta
        val actualTimeDelta = if (timeDelta < 0) timeDelta + 65536 else timeDelta

        if (actualTimeDelta == 0) return null

        // Time is in 1/1024 second resolution, convert to minutes
        val timeInMinutes = actualTimeDelta / 1024.0 / 60.0
        return actualRevDelta / timeInMinutes
    }

    /**
     * Returns true if this measurement includes left/right power balance data.
     */
    val hasBalance: Boolean
        get() = pedalPowerBalance != null

    /**
     * Returns true if this measurement includes crank revolution data for cadence calculation.
     */
    val hasCadenceData: Boolean
        get() = crankRevolutions != null && lastCrankEventTime != null
}
