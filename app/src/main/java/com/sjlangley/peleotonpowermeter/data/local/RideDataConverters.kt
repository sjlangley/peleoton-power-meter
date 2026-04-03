package com.sjlangley.peleotonpowermeter.data.local

import androidx.room.TypeConverter
import com.sjlangley.peleotonpowermeter.data.model.AsymmetryInterval

class RideDataConverters {
    @TypeConverter
    fun fromTimeInZoneSeconds(value: Map<Int, Int>): String =
        value.entries
            .sortedBy { it.key }
            .joinToString(separator = ";") { "${it.key}:${it.value}" }

    @TypeConverter
    fun toTimeInZoneSeconds(value: String): Map<Int, Int> =
        value
            .takeIf { it.isNotBlank() }
            ?.split(";")
            ?.associate { entry ->
                val (zone, seconds) = entry.split(":")
                zone.toInt() to seconds.toInt()
            }
            ?: emptyMap()

    @TypeConverter
    fun fromAsymmetryIntervals(value: List<AsymmetryInterval>): String =
        value.joinToString(separator = ";") { interval ->
            listOf(
                interval.startLabel,
                interval.endLabel,
                interval.leftPercent.toString(),
                interval.rightPercent.toString(),
                interval.supported.toString(),
            ).joinToString(separator = "|")
        }

    @TypeConverter
    fun toAsymmetryIntervals(value: String): List<AsymmetryInterval> =
        value
            .takeIf { it.isNotBlank() }
            ?.split(";")
            ?.map { entry ->
                val parts = entry.split("|")
                AsymmetryInterval(
                    startLabel = parts[0],
                    endLabel = parts[1],
                    leftPercent = parts[2].toInt(),
                    rightPercent = parts[3].toInt(),
                    supported = parts[4].toBoolean(),
                )
            }
            ?: emptyList()
}
