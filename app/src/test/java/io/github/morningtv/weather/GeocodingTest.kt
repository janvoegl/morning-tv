package io.github.morningtv.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeocodingTest {
    private val reply = """{"results":[
        {"name":"München","latitude":48.13743,"longitude":11.57549,"timezone":"Europe/Berlin","admin1":"Bayern","country":"Deutschland"},
        {"name":"Berlin","latitude":52.52437,"longitude":13.41053,"timezone":"Europe/Berlin","admin1":"Berlin","country":"Deutschland"},
        {"name":"NoCoords"}
    ],"generationtime_ms":0.5}"""

    @Test
    fun parsesPlacesAndSkipsIncomplete() {
        val p = parsePlaces(reply)
        assertEquals(2, p.size)
        assertEquals(Place("München", "München, Bayern, Deutschland", 48.13743, 11.57549, "Europe/Berlin"), p[0])
        assertEquals("Berlin, Deutschland", p[1].label)
    }

    @Test
    fun noHitsOrGarbageGivesEmpty() {
        assertTrue(parsePlaces("""{"generationtime_ms":0.2}""").isEmpty())
        assertTrue(parsePlaces("{oops").isEmpty())
    }

    @Test
    fun urlEncodesQuery() {
        assertEquals(
            "https://geocoding-api.open-meteo.com/v1/search?name=Bad+T%C3%B6lz&count=6&language=de&format=json",
            geocodingUrl(" Bad Tölz ", "de"),
        )
    }
}
