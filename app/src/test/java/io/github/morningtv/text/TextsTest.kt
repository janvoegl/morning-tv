package io.github.morningtv.text

import io.github.morningtv.calendar.Agenda
import io.github.morningtv.calendar.AgendaItem
import io.github.morningtv.calendar.parseAgenda
import io.github.morningtv.standby.countdownLabel
import io.github.morningtv.ui.AgendaRow
import io.github.morningtv.ui.agendaRows
import io.github.morningtv.ui.greeting
import io.github.morningtv.ui.rainLine
import io.github.morningtv.weather.WeatherSummary
import io.github.morningtv.weather.weatherLabel
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TextsTest {
    private val de = Texts.DE

    @Test
    fun picksGermanForAnyGermanLocale() {
        assertSame(Texts.DE, Texts.forLocale(Locale.forLanguageTag("de-DE")))
        assertSame(Texts.DE, Texts.forLocale(Locale.forLanguageTag("de-AT")))
    }

    @Test
    fun fallsBackToEnglish() {
        assertSame(Texts.EN, Texts.forLocale(Locale.forLanguageTag("en-GB")))
        assertSame(Texts.EN, Texts.forLocale(Locale.forLanguageTag("fr-FR")))
    }

    @Test
    fun germanScreenTexts() {
        assertEquals("Guten Morgen, Jan", greeting("Jan", de))
        assertEquals("Guten Morgen", greeting(" ", de))
        assertEquals(listOf(AgendaRow("", "Heute keine Termine")), agendaRows(Agenda(emptyList(), emptyList()), de))
        val a = Agenda(listOf(AgendaItem("Sport", "18:00", false)), listOf(AgendaItem("Geburtstag", "", true)))
        assertEquals(listOf(AgendaRow("18:00", "Sport"), AgendaRow("ganztägig", "Geburtstag")), agendaRows(a, de))
        val many = (1..11).map { AgendaItem("E$it", "%02d:00".format(it + 6), false) }
        assertEquals(AgendaRow("", "+3 weitere"), agendaRows(Agenda(many, emptyList()), de).last())
    }

    @Test
    fun germanWeatherAndCountdown() {
        assertEquals("Regen 60 % ab 14:00", rainLine(WeatherSummary(4, 3, 8, 1, 60, 14, emptyList()), de))
        assertEquals("Kein Regen erwartet", rainLine(WeatherSummary(4, 3, 8, 1, 0, null, emptyList()), de))
        assertEquals("Teils bewölkt", weatherLabel(2, de))
        assertEquals("TV aus in 30 Min.", countdownLabel(29 * 60_000L + 1_000, de))
        assertEquals("TV aus in <1 Min.", countdownLabel(0, de))
    }

    @Test
    fun germanUntilLabelForEventFromYesterday() {
        val zone = ZoneId.of("Europe/Vienna")
        val payload = """{"events":[{"title":"Nachtdienst","start":"2026-11-22T22:00:00+01:00","end":"2026-11-23T07:00:00+01:00"}]}"""
        val agenda = parseAgenda(payload, LocalDate.of(2026, 11, 23), zone, de)!!
        assertEquals("bis 07:00", agenda.timed.single().timeLabel)
    }

    @Test
    fun germanClock() {
        val time = ZonedDateTime.of(2026, 11, 23, 6, 45, 0, 0, ZoneId.of("Europe/Vienna"))
        assertEquals("Mo. 23. Nov.  06:45", time.format(DateTimeFormatter.ofPattern(de.clockPattern, de.locale)))
    }
}
