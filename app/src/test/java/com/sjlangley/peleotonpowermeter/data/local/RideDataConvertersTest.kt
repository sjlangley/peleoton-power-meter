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

    @Test
    fun toTimeInZoneSeconds_handlesTrailingSemicolon() {
        val result = converters.toTimeInZoneSeconds("1:30;2:45;")
        assertEquals(mapOf(1 to 30, 2 to 45), result)
    }

    @Test
    fun toTimeInZoneSeconds_skipsMissingColon() {
        val result = converters.toTimeInZoneSeconds("1:30;invalid;2:45")
        assertEquals(mapOf(1 to 30, 2 to 45), result)
    }

    @Test
    fun toTimeInZoneSeconds_skipsMultipleColons() {
        val result = converters.toTimeInZoneSeconds("1:30;2:45:60;3:90")
        assertEquals(mapOf(1 to 30, 3 to 90), result)
    }

    @Test
    fun toTimeInZoneSeconds_skipsNonNumericValues() {
        val result = converters.toTimeInZoneSeconds("1:30;abc:def;2:45;3:xyz")
        assertEquals(mapOf(1 to 30, 2 to 45), result)
    }

    @Test
    fun toTimeInZoneSeconds_handlesAllInvalidData() {
        assertEquals(emptyMap<Int, Int>(), converters.toTimeInZoneSeconds("invalid;abc:def;123"))
    }

    @Test
    fun toTimeInZoneSeconds_handlesPartialMigrationData() {
        val result = converters.toTimeInZoneSeconds("1:120;2:;:300;4:180")
        assertEquals(mapOf(1 to 120, 4 to 180), result)
    }

    @Test
    fun toAsymmetryIntervals_handlesTrailingSemicolon() {
        val result = converters.toAsymmetryIntervals("00:30|00:59|54|46|true;")
        assertEquals(
            listOf(AsymmetryInterval("00:30", "00:59", 54, 46, true)),
            result,
        )
    }

    @Test
    fun toAsymmetryIntervals_skipsMissingPipes() {
        val result = converters.toAsymmetryIntervals("00:30|00:59|54|46|true;invalid;01:00|01:30|48|52|false")
        assertEquals(
            listOf(
                AsymmetryInterval("00:30", "00:59", 54, 46, true),
                AsymmetryInterval("01:00", "01:30", 48, 52, false),
            ),
            result,
        )
    }

    @Test
    fun toAsymmetryIntervals_skipsTooFewParts() {
        val result = converters.toAsymmetryIntervals("00:30|00:59|54|46|true;00:30|00:59|54;01:00|01:30|48|52|false")
        assertEquals(
            listOf(
                AsymmetryInterval("00:30", "00:59", 54, 46, true),
                AsymmetryInterval("01:00", "01:30", 48, 52, false),
            ),
            result,
        )
    }

    @Test
    fun toAsymmetryIntervals_skipsTooManyParts() {
        val result = converters.toAsymmetryIntervals("00:30|00:59|54|46|true;00:30|00:59|54|46|true|extra;01:00|01:30|48|52|false")
        assertEquals(
            listOf(
                AsymmetryInterval("00:30", "00:59", 54, 46, true),
                AsymmetryInterval("01:00", "01:30", 48, 52, false),
            ),
            result,
        )
    }

    @Test
    fun toAsymmetryIntervals_skipsNonNumericPercents() {
        val result = converters.toAsymmetryIntervals("00:30|00:59|54|46|true;00:30|00:59|abc|def|false;01:00|01:30|48|52|false")
        assertEquals(
            listOf(
                AsymmetryInterval("00:30", "00:59", 54, 46, true),
                AsymmetryInterval("01:00", "01:30", 48, 52, false),
            ),
            result,
        )
    }

    @Test
    fun toAsymmetryIntervals_handlesAllInvalidData() {
        assertEquals(emptyList<AsymmetryInterval>(), converters.toAsymmetryIntervals("invalid;abc|def;123"))
    }

    @Test
    fun toAsymmetryIntervals_handlesPartialMigrationData() {
        val result = converters.toAsymmetryIntervals("00:30|00:59|54|46|true;|00:59|54|46|true;00:30||54|46|true;01:00|01:30|48|52|false")
        assertEquals(
            listOf(
                AsymmetryInterval("00:30", "00:59", 54, 46, true),
                AsymmetryInterval("01:00", "01:30", 48, 52, false),
            ),
            result,
        )
    }

    @Test
    fun toAsymmetryIntervals_handlesBooleanEdgeCases() {
        val result = converters.toAsymmetryIntervals("00:30|00:59|54|46|true;01:00|01:30|48|52|false;02:00|02:30|50|50|1;03:00|03:30|49|51|0")
        assertEquals(
            listOf(
                AsymmetryInterval("00:30", "00:59", 54, 46, true),
                AsymmetryInterval("01:00", "01:30", 48, 52, false),
                AsymmetryInterval("02:00", "02:30", 50, 50, false),
                AsymmetryInterval("03:00", "03:30", 49, 51, false),
            ),
            result,
        )
    }
}
