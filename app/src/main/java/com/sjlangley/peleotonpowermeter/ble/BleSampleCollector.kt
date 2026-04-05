package com.sjlangley.peleotonpowermeter.ble

import com.sjlangley.peleotonpowermeter.data.model.RideSample
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collects BLE notifications from power meters and heart rate monitors and normalizes
 * them to 1-second [RideSample] intervals.
 *
 * BLE notifications arrive at irregular intervals (often sub-second for power, less
 * frequent for HR). This collector maintains the most recent data from each sensor
 * and generates normalized 1-second samples on demand.
 *
 * @param ftpWatts Functional Threshold Power in watts for zone calculation
 */
class BleSampleCollector(
    private val ftpWatts: Int,
) {
    private val mutex = Mutex()
    
    // Most recent data from each sensor
    private var leftPowerData: CyclingPowerData? = null
    private var leftPreviousPowerData: CyclingPowerData? = null
    private var rightPowerData: CyclingPowerData? = null
    private var rightPreviousPowerData: CyclingPowerData? = null
    private var heartRateData: HeartRateData? = null
    
    // Connection states
    private var leftConnected: Boolean = false
    private var rightConnected: Boolean = false
    private var heartRateConnected: Boolean = false
    
    /**
     * Update left pedal data.
     */
    suspend fun updateLeftPower(data: CyclingPowerData?) {
        mutex.withLock {
            leftPreviousPowerData = leftPowerData
            leftPowerData = data
        }
    }
    
    /**
     * Update right pedal data.
     */
    suspend fun updateRightPower(data: CyclingPowerData?) {
        mutex.withLock {
            rightPreviousPowerData = rightPowerData
            rightPowerData = data
        }
    }
    
    /**
     * Update heart rate data.
     */
    suspend fun updateHeartRate(data: HeartRateData?) {
        mutex.withLock {
            heartRateData = data
        }
    }
    
    /**
     * Update connection state for left pedal.
     */
    suspend fun setLeftConnected(connected: Boolean) {
        mutex.withLock {
            leftConnected = connected
            if (!connected) {
                leftPowerData = null
            }
        }
    }
    
    /**
     * Update connection state for right pedal.
     */
    suspend fun setRightConnected(connected: Boolean) {
        mutex.withLock {
            rightConnected = connected
            if (!connected) {
                rightPowerData = null
            }
        }
    }
    
    /**
     * Update connection state for heart rate monitor.
     */
    suspend fun setHeartRateConnected(connected: Boolean) {
        mutex.withLock {
            heartRateConnected = connected
            if (!connected) {
                heartRateData = null
            }
        }
    }
    
    /**
     * Generate a normalized 1-second sample from the current sensor data.
     *
     * @param timestampEpochSeconds The timestamp for this sample
     * @return A normalized ride sample with the most recent data from all sensors
     */
    suspend fun generateSample(timestampEpochSeconds: Long): RideSample {
        return mutex.withLock {
            val leftPower = leftPowerData?.instantaneousPower
            val rightPower = rightPowerData?.instantaneousPower
            
            // Calculate total power: use sum if both available, otherwise use what we have
            val totalPower = when {
                leftPower != null && rightPower != null -> leftPower + rightPower
                leftPower != null -> leftPower
                rightPower != null -> rightPower
                else -> 0
            }
            
            // Calculate cadence - prefer right pedal, fall back to left
            val cadence = rightPowerData?.calculateCadence(rightPreviousPowerData)?.toInt()
                ?: leftPowerData?.calculateCadence(leftPreviousPowerData)?.toInt()
            
            // Get heart rate
            val heartRate = heartRateData?.heartRateBpm
            
            // Calculate zone index (0-6 for Zones 1-7)
            val zoneIndex = calculateZoneIndex(totalPower, ftpWatts)
            
            RideSample(
                timestampEpochSeconds = timestampEpochSeconds,
                leftPowerWatts = leftPower,
                rightPowerWatts = rightPower,
                totalPowerWatts = totalPower,
                cadenceRpm = cadence,
                heartRateBpm = heartRate,
                zoneIndex = zoneIndex,
                leftConnected = leftConnected,
                rightConnected = rightConnected,
                heartRateConnected = heartRateConnected,
            )
        }
    }
    
    /**
     * Reset all sensor data and connection states.
     */
    suspend fun reset() {
        mutex.withLock {
            leftPowerData = null
            leftPreviousPowerData = null
            rightPowerData = null
            rightPreviousPowerData = null
            heartRateData = null
            leftConnected = false
            rightConnected = false
            heartRateConnected = false
        }
    }
    
    private fun calculateZoneIndex(powerWatts: Int, ftpWatts: Int): Int {
        if (ftpWatts <= 0) {
            return 0 // Default to Zone 1 if FTP is invalid
        }
        
        val percentFtp = (powerWatts.toFloat() / ftpWatts) * 100f
        
        return when {
            percentFtp < 55f -> 0  // Zone 1: Active Recovery (<55% FTP)
            percentFtp < 75f -> 1  // Zone 2: Endurance (55-74%)
            percentFtp < 90f -> 2  // Zone 3: Tempo (75-89%)
            percentFtp < 105f -> 3 // Zone 4: Threshold (90-104%)
            percentFtp < 120f -> 4 // Zone 5: VO2 Max (105-119%)
            percentFtp < 150f -> 5 // Zone 6: Anaerobic (120-149%)
            else -> 6             // Zone 7: Neuromuscular (150%+)
        }
    }
}
