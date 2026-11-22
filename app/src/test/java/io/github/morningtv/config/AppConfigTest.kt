package io.github.morningtv.config

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {
    @Test
    fun fromMapReadsAllKeys() {
        val cfg = AppConfig.fromMap(
            mapOf(
                "tv_psk" to "psk123",
                "receiver_token" to "tok456",
                "greeting_name" to "Alex",
                "latitude" to "48.2082",
                "longitude" to "16.3738",
                "location_name" to "Vienna",
                "timezone" to "Europe/Vienna",
                "standby_minutes" to "45",
            )
        )
        assertEquals("psk123", cfg.tvPsk)
        assertEquals("tok456", cfg.receiverToken)
        assertEquals("Alex", cfg.greetingName)
        assertEquals(48.2082, cfg.latitude, 1e-9)
        assertEquals(16.3738, cfg.longitude, 1e-9)
        assertEquals("Vienna", cfg.locationName)
        assertEquals(ZoneId.of("Europe/Vienna"), cfg.timezone)
        assertEquals(45, cfg.standbyMinutes)
    }

    @Test
    fun emptyMapGivesDefaults() {
        val cfg = AppConfig.fromMap(emptyMap())
        assertEquals(30, cfg.standbyMinutes)
        assertEquals("", cfg.greetingName)
        assertEquals("", cfg.tvPsk)
        assertTrue(cfg.latitude.isNaN())
        assertTrue(cfg.longitude.isNaN())
        assertEquals(ZoneId.systemDefault(), cfg.timezone)
    }

    @Test
    fun invalidStandbyMinutesFallsBackTo30() {
        assertEquals(30, AppConfig.fromMap(mapOf("standby_minutes" to "abc")).standbyMinutes)
        assertEquals(30, AppConfig.fromMap(mapOf("standby_minutes" to "0")).standbyMinutes)
    }

    @Test
    fun invalidTimezoneFallsBackToSystemDefault() {
        assertEquals(ZoneId.systemDefault(), AppConfig.fromMap(mapOf("timezone" to "Mars/Base")).timezone)
    }

    @Test
    fun invalidCoordinatesBecomeNaN() {
        assertTrue(AppConfig.fromMap(mapOf("latitude" to "north")).latitude.isNaN())
    }
}
