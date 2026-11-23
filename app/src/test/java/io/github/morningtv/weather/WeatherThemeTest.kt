package io.github.morningtv.weather

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherThemeTest {

    @Test
    fun mapsEveryCodeGroup() {
        assertEquals(WeatherTheme.CLEAR, weatherTheme(0))
        assertEquals(WeatherTheme.PARTLY, weatherTheme(1))
        assertEquals(WeatherTheme.PARTLY, weatherTheme(2))
        assertEquals(WeatherTheme.CLOUDY, weatherTheme(3))
        assertEquals(WeatherTheme.FOG, weatherTheme(45))
        assertEquals(WeatherTheme.FOG, weatherTheme(48))
        assertEquals(WeatherTheme.RAIN, weatherTheme(51))
        assertEquals(WeatherTheme.RAIN, weatherTheme(61))
        assertEquals(WeatherTheme.RAIN, weatherTheme(67))
        assertEquals(WeatherTheme.SNOW, weatherTheme(71))
        assertEquals(WeatherTheme.SNOW, weatherTheme(77))
        assertEquals(WeatherTheme.RAIN, weatherTheme(80))
        assertEquals(WeatherTheme.SNOW, weatherTheme(85))
        assertEquals(WeatherTheme.SNOW, weatherTheme(86))
        assertEquals(WeatherTheme.STORM, weatherTheme(95))
        assertEquals(WeatherTheme.STORM, weatherTheme(99))
        assertEquals(WeatherTheme.CLOUDY, weatherTheme(200))
    }

    @Test
    fun gradientColorsMatchHexValues() {
        assertArrayEquals(
            intArrayOf(0xFF5B2BD6.toInt(), 0xFFC13A8F.toInt(), 0xFFFF7A3D.toInt()),
            gradientColors(WeatherTheme.CLEAR)
        )
        assertArrayEquals(
            intArrayOf(0xFF3B3FD0.toInt(), 0xFF7C6BE0.toInt(), 0xFFF2A06B.toInt()),
            gradientColors(WeatherTheme.PARTLY)
        )
        assertArrayEquals(
            intArrayOf(0xFF47556F.toInt(), 0xFF69789A.toInt(), 0xFF8E9AB4.toInt()),
            gradientColors(WeatherTheme.CLOUDY)
        )
        assertArrayEquals(
            intArrayOf(0xFF1B3466.toInt(), 0xFF2F5C9E.toInt(), 0xFF4E86B8.toInt()),
            gradientColors(WeatherTheme.RAIN)
        )
        assertArrayEquals(
            intArrayOf(0xFF46689A.toInt(), 0xFF7B9CC4.toInt(), 0xFFA9C3E0.toInt()),
            gradientColors(WeatherTheme.SNOW)
        )
        assertArrayEquals(
            intArrayOf(0xFF241657.toInt(), 0xFF4B2C8F.toInt(), 0xFF7A4FB0.toInt()),
            gradientColors(WeatherTheme.STORM)
        )
        assertArrayEquals(
            intArrayOf(0xFF5E6879.toInt(), 0xFF818B9C.toInt(), 0xFFA3ACB9.toInt()),
            gradientColors(WeatherTheme.FOG)
        )
    }

    @Test
    fun gradientColorsHaveLengthThree() {
        for (theme in WeatherTheme.values()) {
            val colors = gradientColors(theme)
            assertEquals(3, colors.size)
        }
    }
}
