package io.github.morningtv.ui

import io.github.morningtv.config.AppConfig
import io.github.morningtv.config.CalendarStyle
import io.github.morningtv.config.LanguageSetting
import io.github.morningtv.config.ScreenLayout
import io.github.morningtv.text.SettingsTexts
import io.github.morningtv.weather.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsTest {
    private val st = SettingsTexts.DE
    private fun cfg(vararg kv: Pair<String, String>) = AppConfig.fromMap(mapOf(*kv))

    @Test
    fun rowsShowCurrentValues() {
        val rows = settingRows(cfg("greeting_name" to "Jan", "location_name" to "München", "standby_minutes" to "45", "layout" to "classic"), st)
        assertEquals(listOf("Jan", "München", "45 Min.", "Automatisch", "Klassisch"), rows.take(5).map { it.value })
        assertEquals(SettingId.values().toList(), rows.map { it.id })
    }

    @Test
    fun emptyNameAndPlaceShowNotSet() {
        val rows = settingRows(cfg(), st)
        assertEquals("Nicht gesetzt", rows[0].value)
        assertEquals("Nicht gesetzt", rows[1].value)
    }

    @Test
    fun googleAndWeatherLinkUnavailableWithoutSetup() {
        val rows = settingRows(cfg(), st)
        assertEquals(RowKind.UNAVAILABLE, rows.single { it.id == SettingId.GOOGLE }.kind)
        assertEquals(RowKind.UNAVAILABLE, rows.single { it.id == SettingId.WEATHERLINK }.kind)
        val on = settingRows(cfg("google_ical_url" to "https://x/y.ics", "weatherlink_host" to "10.0.0.9", "google_enabled" to "false"), st)
        assertEquals("Aus", on.single { it.id == SettingId.GOOGLE }.value)
        assertEquals("An", on.single { it.id == SettingId.WEATHERLINK }.value)
    }

    @Test
    fun standbyCyclesAndWraps() {
        assertEquals("standby_minutes" to "45", changeChoice(cfg("standby_minutes" to "30"), SettingId.STANDBY, 1))
        assertEquals("standby_minutes" to "20", changeChoice(cfg("standby_minutes" to "30"), SettingId.STANDBY, -1))
        assertEquals("standby_minutes" to "10", changeChoice(cfg("standby_minutes" to "90"), SettingId.STANDBY, 1))
        assertEquals("standby_minutes" to "90", changeChoice(cfg("standby_minutes" to "10"), SettingId.STANDBY, -1))
    }

    @Test
    fun standbyOffListStepsToNearestChoice() {
        assertEquals("standby_minutes" to "45", changeChoice(cfg("standby_minutes" to "35"), SettingId.STANDBY, 1))
        assertEquals("standby_minutes" to "30", changeChoice(cfg("standby_minutes" to "35"), SettingId.STANDBY, -1))
        assertEquals("standby_minutes" to "10", changeChoice(cfg("standby_minutes" to "500"), SettingId.STANDBY, 1))
        assertEquals("standby_minutes" to "90", changeChoice(cfg("standby_minutes" to "500"), SettingId.STANDBY, -1))
        assertEquals("standby_minutes" to "90", changeChoice(cfg("standby_minutes" to "5"), SettingId.STANDBY, -1))
    }

    @Test
    fun languageLayoutStyleCycle() {
        assertEquals("language" to "de", changeChoice(cfg(), SettingId.LANGUAGE, 1))
        assertEquals("language" to "auto", changeChoice(cfg("language" to "en"), SettingId.LANGUAGE, 1))
        assertEquals("language" to "en", changeChoice(cfg(), SettingId.LANGUAGE, -1))
        assertEquals("layout" to "classic", changeChoice(cfg(), SettingId.LAYOUT, 1))
        assertEquals("layout" to "timeline", changeChoice(cfg("layout" to "classic"), SettingId.LAYOUT, 1))
        assertEquals("calendar_style" to "list", changeChoice(cfg(), SettingId.CALENDAR_STYLE, 1))
    }

    @Test
    fun switchesToggleOnlyWhenSetUp() {
        assertNull(changeChoice(cfg(), SettingId.GOOGLE, 1))
        assertNull(changeChoice(cfg(), SettingId.WEATHERLINK, 1))
        assertEquals("google_enabled" to "false", changeChoice(cfg("google_ical_url" to "https://x"), SettingId.GOOGLE, 1))
        assertEquals("weatherlink_enabled" to "true", changeChoice(cfg("weatherlink_host" to "h", "weatherlink_enabled" to "false"), SettingId.WEATHERLINK, 1))
    }

    @Test
    fun textRowsAreNotChoices() {
        assertNull(changeChoice(cfg(), SettingId.NAME, 1))
        assertNull(changeChoice(cfg(), SettingId.PLACE, 1))
    }

    @Test
    fun placeUpdatesIncludeTimezoneOnlyWhenKnown() {
        assertEquals(
            listOf("latitude" to "48.1", "longitude" to "11.5", "location_name" to "München", "timezone" to "Europe/Berlin"),
            placeUpdates(Place("München", "München, Bayern", 48.1, 11.5, "Europe/Berlin")),
        )
        assertEquals(4 - 1, placeUpdates(Place("X", "X", 1.0, 2.0, "")).size)
    }
}
