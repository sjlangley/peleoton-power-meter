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
            ?.mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size != 2) return@mapNotNull null
                val zone = parts[0].toIntOrNull() ?: return@mapNotNull null
                val seconds = parts[1].toIntOrNull() ?: return@mapNotNull null
                zone to seconds
            }
            ?.toMap()
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
            ?.mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size != 5) return@mapNotNull null
                if (parts[0].isBlank() || parts[1].isBlank()) return@mapNotNull null
                val leftPercent = parts[2].toIntOrNull() ?: return@mapNotNull null
                val rightPercent = parts[3].toIntOrNull() ?: return@mapNotNull null
                AsymmetryInterval(
                    startLabel = parts[0],
                    endLabel = parts[1],
                    leftPercent = leftPercent,
                    rightPercent = rightPercent,
                    supported = parts[4].toBoolean(),
                )
            }
            ?: emptyList()
}
