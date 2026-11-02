package com.classsoftwarehub.android.util.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConverterTest {

    private fun assertConvert(
        category: UnitCategory,
        from: String,
        to: String,
        value: Double,
        expected: Double,
        delta: Double = 1e-9,
    ) {
        val actual = UnitConverter.convert(category, from, to, value)
        requireNotNull(actual)
        assertEquals(expected, actual, delta)
    }

    @Test
    fun lengthKilometreToMetre() {
        assertConvert(UnitCategory.LENGTH, "km", "m", 1.0, 1000.0)
        assertConvert(UnitCategory.LENGTH, "inch", "cm", 1.0, 2.54)
    }

    @Test
    fun massJinToGram() {
        assertConvert(UnitCategory.MASS, "jin", "g", 1.0, 500.0)
        assertConvert(UnitCategory.MASS, "kg", "jin", 1.0, 2.0)
    }

    @Test
    fun areaMuToSquareMetre() {
        assertConvert(UnitCategory.AREA, "mu", "m2", 1.0, 2000.0 / 3.0, 1e-6)
        assertConvert(UnitCategory.AREA, "ha", "mu", 1.0, 15.0, 1e-9)
    }

    @Test
    fun temperatureRoundTrip() {
        assertConvert(UnitCategory.TEMPERATURE, "c", "f", 0.0, 32.0)
        assertConvert(UnitCategory.TEMPERATURE, "c", "f", 100.0, 212.0)
        assertConvert(UnitCategory.TEMPERATURE, "f", "c", 32.0, 0.0)
        assertConvert(UnitCategory.TEMPERATURE, "c", "k", 0.0, 273.15)
    }

    @Test
    fun dataUsesBinaryUnits() {
        assertConvert(UnitCategory.DATA, "gb", "mb", 1.0, 1024.0)
        assertConvert(UnitCategory.DATA, "mb", "b", 1.0, 1_048_576.0)
    }

    @Test
    fun timeAndSpeed() {
        assertConvert(UnitCategory.TIME, "h", "min", 1.0, 60.0)
        assertConvert(UnitCategory.SPEED, "kmh", "mps", 36.0, 10.0)
    }

    @Test
    fun unknownUnitsReturnNull() {
        assertNull(UnitConverter.convert(UnitCategory.LENGTH, "lightyear", "m", 1.0))
        assertNull(UnitConverter.convert(UnitCategory.LENGTH, "m", "lightyear", 1.0))
    }

    @Test
    fun formattingKeepsSixDecimals() {
        assertEquals("1000", UnitConverter.formatNumber(1000.0))
        assertEquals("0.5", UnitConverter.formatNumber(0.5))
        assertEquals("666.666667", UnitConverter.formatNumber(2000.0 / 3.0))
        assertEquals("0", UnitConverter.formatNumber(0.0))
    }
}
