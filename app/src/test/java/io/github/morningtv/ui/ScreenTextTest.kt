package io.github.morningtv.ui

import io.github.morningtv.calendar.Agenda
import io.github.morningtv.calendar.AgendaItem
import io.github.morningtv.weather.WeatherSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTextTest {
    @Test
    fun greetingWithName() = assertEquals("Good morning, Alex", greeting("Alex"))

    @Test
    fun greetingBlankName() = assertEquals("Good morning", greeting("  "))

    @Test
    fun manualStartExplainsWhereAppointmentsComeFrom() {
        assertEquals(listOf(AgendaRow("", "Appointments arrive with your alarm")), agendaRows(null))
    }

    @Test
    fun emptyAgendaSaysNoAppointments() {
        assertEquals(listOf(AgendaRow("", "No appointments today")), agendaRows(Agenda(emptyList(), emptyList())))
    }

    @Test
    fun timedThenAllDay() {
        val a = Agenda(listOf(AgendaItem("Gym", "18:00", false)), listOf(AgendaItem("Birthday", "", true)))
        assertEquals(listOf(AgendaRow("18:00", "Gym"), AgendaRow("all day", "Birthday")), agendaRows(a))
    }

    @Test
    fun moreThanEightTimedIsCut() {
        val items = (1..11).map { AgendaItem("E$it", "%02d:00".format(it + 6), false) }
        val rows = agendaRows(Agenda(items, emptyList()))
        assertEquals(9, rows.size)
        assertEquals(AgendaRow("", "+3 more"), rows.last())
        assertEquals("E8", rows[7].title)
    }

    private fun weather(chance: Int, from: Int?) = WeatherSummary(4, 3, 8, 1, chance, from, emptyList())

    @Test
    fun rainLineWithStartHour() = assertEquals("Rain 60% from 14:00", rainLine(weather(60, 14)))

    @Test
    fun rainLineWithoutStartHour() = assertEquals("Rain 20%", rainLine(weather(20, null)))

    @Test
    fun rainLineNoRain() = assertEquals("No rain expected", rainLine(weather(0, null)))

    @Test
    fun greetingFollowsTheHour() {
        val de = io.github.morningtv.text.Texts.DE
        assertEquals("Hallo, Jan", greeting("Jan", de, 3))
        assertEquals("Guten Morgen, Jan", greeting("Jan", de, 4))
        assertEquals("Guten Morgen", greeting("", de, 5))
        assertEquals("Guten Morgen", greeting("", de, 10))
        assertEquals("Guten Tag", greeting(" ", de, 11))
        assertEquals("Guten Tag, Jan", greeting("Jan", de, 17))
        assertEquals("Guten Abend, Jan", greeting("Jan", de, 18))
        assertEquals("Guten Abend", greeting("", de, 21))
        assertEquals("Hallo", greeting("", de, 22))
        assertEquals("Good afternoon, Sam", greeting("Sam", io.github.morningtv.text.Texts.EN, 14))
    }
}
