package io.github.morningtv.weather

import io.github.morningtv.config.AppConfig
import java.io.IOException
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    private val fixture = javaClass.getResource("/open-meteo-sample.json")!!.readText()
    private val cfg = AppConfig.fromMap(
        mapOf("latitude" to "48.2082", "longitude" to "16.3738", "timezone" to "Europe/Vienna")
    )

    @Test
    fun urlContainsCoordinatesAndZone() {
        val url = forecastUrl(48.2082, 16.3738, ZoneId.of("Europe/Vienna"))
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?"))
        assertTrue(url.contains("latitude=48.2082"))
        assertTrue(url.contains("longitude=16.3738"))
        assertTrue(url.contains("timezone=Europe%2FVienna"))
        assertTrue(url.contains("current=temperature_2m,weather_code"))
        assertTrue(url.contains("hourly=temperature_2m,precipitation_probability,weather_code"))
        assertTrue(url.contains("daily=temperature_2m_max,temperature_2m_min"))
        assertTrue(url.contains("forecast_days=1"))
    }

    @Test
    fun parsesFixture() {
        val w = parseForecast(fixture, nowHour = 7)!!
        assertEquals(4, w.nowTempC)
        assertEquals(3, w.nowCode)
        assertEquals(8, w.highC)
        assertEquals(1, w.lowC)
        assertEquals(60, w.rainChance)
        assertEquals(14, w.rainFromHour)
        assertEquals(
            listOf(HourPoint(7, 2, 3), HourPoint(9, 3, 3), HourPoint(12, 5, 3), HourPoint(15, 6, 61), HourPoint(18, 8, 3)),
            w.hours
        )
    }

    @Test
    fun rainFromHourNullWhenBelow30() {
        val w = parseForecast(fixture, nowHour = 16)!!
        assertNull(w.rainFromHour)
        assertEquals(20, w.rainChance)
    }

    @Test
    fun hoursSkipPast() {
        assertEquals(listOf(12, 15, 18), parseForecast(fixture, nowHour = 10)!!.hours.map { it.hour })
    }

    @Test
    fun malformedReturnsNull() {
        assertNull(parseForecast("{oops", 7))
        assertNull(parseForecast("""{"current":{}}""", 7))
    }

    @Test
    fun labelsAndIcons() {
        assertEquals("Clear", weatherLabel(0))
        assertEquals("Cloudy", weatherLabel(3))
        assertEquals("Fog", weatherLabel(45))
        assertEquals("Drizzle", weatherLabel(53))
        assertEquals("Rain", weatherLabel(61))
        assertEquals("Snow", weatherLabel(73))
        assertEquals("Showers", weatherLabel(81))
        assertEquals("Thunderstorm", weatherLabel(95))
        assertEquals("☀", weatherIcon(0))
        assertEquals("🌧", weatherIcon(61))
    }

    @Test
    fun clientRetriesThreeTimesThenNull() {
        var calls = 0
        val sleeps = mutableListOf<Long>()
        val client = WeatherClient(fetch = { calls++; throw IOException("no network") }, sleep = { sleeps += it })
        assertNull(client.load(cfg, 7))
        assertEquals(3, calls)
        assertEquals(listOf(5000L, 5000L), sleeps)
    }

    @Test
    fun clientSucceedsOnSecondTry() {
        var calls = 0
        val client = WeatherClient(fetch = { calls++; if (calls == 1) throw IOException("down") else fixture }, sleep = {})
        assertEquals(4, client.load(cfg, 7)!!.nowTempC)
        assertEquals(2, calls)
    }

    @Test
    fun clientSkipsFetchWithoutLocation() {
        var calls = 0
        val client = WeatherClient(fetch = { calls++; fixture }, sleep = {})
        assertNull(client.load(AppConfig.fromMap(emptyMap()), 7))
        assertEquals(0, calls)
    }
}
