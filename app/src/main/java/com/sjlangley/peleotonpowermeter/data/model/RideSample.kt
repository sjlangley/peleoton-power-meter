package com.sjlangley.peleotonpowermeter.data.model

data class RideSample(
    val timestampEpochSeconds: Long,
    val leftPowerWatts: Int?,
    val rightPowerWatts: Int?,
    val totalPowerWatts: Int,
    val cadenceRpm: Int?,
    val heartRateBpm: Int?,
    val zoneIndex: Int,
    val leftConnected: Boolean,
    val rightConnected: Boolean,
    val heartRateConnected: Boolean,
) {
    val balancePercentLeft: Int?
        get() {
            val left = leftPowerWatts ?: return null
            val right = rightPowerWatts ?: return null
            val total = left + right
            if (total <= 0) {
                return null
            }
            return (left * 100f / total).toInt()
        }
}
