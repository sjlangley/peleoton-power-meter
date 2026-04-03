package com.sjlangley.peleotonpowermeter.data.local

import com.sjlangley.peleotonpowermeter.data.model.AsymmetryInterval
import org.junit.Assert.assertEquals
import org.junit.Test

class RideDataConvertersTest {
    private val converters = RideDataConverters()

    @Test
    fun timeInZoneRoundTripPreservesValues() {
        val original = mapOf(2 to 120, 4 to 480)

        val encoded = converters.fromTimeInZoneSeconds(original)
        val decoded = converters.toTimeInZoneSeconds(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun asymmetryIntervalsRoundTripPreservesValues() {
        val original =
            listOf(
                AsymmetryInterval("00:30", "00:59", leftPercent = 54, rightPercent = 46, supported = true),
                AsymmetryInterval("02:10", "02:39", leftPercent = 44, rightPercent = 56, supported = false),
            )

        val encoded = converters.fromAsymmetryIntervals(original)
        val decoded = converters.toAsymmetryIntervals(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun emptyValuesDecodeSafely() {
        assertEquals(emptyMap<Int, Int>(), converters.toTimeInZoneSeconds(""))
        assertEquals(emptyList<AsymmetryInterval>(), converters.toAsymmetryIntervals(""))
    }
}
