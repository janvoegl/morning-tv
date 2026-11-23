package io.github.morningtv.calendar

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GoogleCalendarTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private val day = LocalDate.of(2026, 11, 23)
    private fun at(h: Int, m: Int = 0, d: Int = 23) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone)

    @Test
    fun mapsTimedAllDayAndMidnightSpanning() {
        val a = icalAgenda(
            listOf(
                IcalOccurrence("Dentist", false, at(11, 30), at(12, 15)),
                IcalOccurrence("Night shift", false, at(22, 0, 22), at(11, 0)),
                IcalOccurrence("Late", false, at(22), at(1, 0, 24)),
                IcalOccurrence("Birthday", true, at(0), at(0, 0, 24)),
            ),
            day, zone,
        )
        assertEquals(
            listOf(
                AgendaItem("Night shift", "until 11:00", false, 0, 660),
                AgendaItem("Dentist", "11:30", false, 690, 735),
                AgendaItem("Late", "22:00", false, 1320, 1440),
            ),
            a.timed,
        )
        assertEquals(listOf(AgendaItem("Birthday", "", true)), a.allDay)
    }

    @Test
    fun mergeDropsDuplicatesAndSorts() {
        val shortcut = Agenda(listOf(AgendaItem("Dentist", "11:30", false, 690, 735)), listOf(AgendaItem("Birthday", "", true)))
        val google = Agenda(
            listOf(AgendaItem("dentist ", "11:30", false, 690, 735), AgendaItem("Standup", "09:00", false, 540, 570)),
            listOf(AgendaItem("Birthday", "", true), AgendaItem("Holiday", "", true)),
        )
        val m = mergeAgendas(shortcut, google)!!
        assertEquals(listOf("Standup", "Dentist"), m.timed.map { it.title })
        assertEquals(listOf("Birthday", "Holiday"), m.allDay.map { it.title })
    }

    @Test
    fun mergeWithNullSide() {
        val a = Agenda(emptyList(), emptyList())
        assertSame(a, mergeAgendas(a, null))
        assertSame(a, mergeAgendas(null, a))
        assertNull(mergeAgendas(null, null))
    }
}
