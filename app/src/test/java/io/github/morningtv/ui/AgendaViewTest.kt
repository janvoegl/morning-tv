package io.github.morningtv.ui

import io.github.morningtv.calendar.Agenda
import io.github.morningtv.calendar.AgendaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaViewTest {
    private val timed = AgendaItem("Dentist", "11:30", false, 690, 735)
    private val allDay = AgendaItem("Birthday", "", true)

    @Test
    fun noDataWaitsForAlarm() {
        assertEquals(AgendaView.Message(AgendaView.Kind.WAITING_FOR_ALARM), agendaView(null))
    }

    @Test
    fun emptyAgendaSaysNoAppointments() {
        assertEquals(AgendaView.Message(AgendaView.Kind.NO_APPOINTMENTS), agendaView(Agenda(emptyList(), emptyList())))
    }

    @Test
    fun oneAppointmentIsSingleWhetherTimedOrAllDay() {
        assertEquals(AgendaView.Single(timed, null), agendaView(Agenda(listOf(timed), emptyList())))
        assertEquals(AgendaView.Single(allDay, "06:45"), agendaView(Agenda(emptyList(), listOf(allDay)), "06:45"))
    }

    @Test
    fun twoOrMoreIsCard() {
        val a = Agenda(listOf(timed), listOf(allDay))
        assertEquals(AgendaView.Card(a, "06:45"), agendaView(a, "06:45"))
    }
}
