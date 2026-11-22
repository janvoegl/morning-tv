package io.github.morningtv.ui

import io.github.morningtv.calendar.Agenda
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchStateTest {
    private val friday = LocalDate.of(2026, 11, 27)
    private val agenda = Agenda(emptyList(), emptyList())

    @Test
    fun agendaFromTodayIsShown() {
        assertEquals(agenda, agendaForToday(DatedAgenda(friday, agenda), friday))
    }

    @Test
    fun agendaFromYesterdayIsDropped() {
        assertNull(agendaForToday(DatedAgenda(friday, agenda), friday.plusDays(1)))
    }

    @Test
    fun noAgendaStaysNull() {
        assertNull(agendaForToday(null, friday))
    }

    @Test
    fun freshLaunchCounts() {
        assertTrue(isFreshLaunch(flags = 0x10000000, restored = false))
    }

    @Test
    fun relaunchFromRecentsDoesNotCount() {
        assertFalse(isFreshLaunch(flags = 0x10000000 or 0x00100000, restored = false))
    }

    @Test
    fun restoredActivityDoesNotCount() {
        assertFalse(isFreshLaunch(flags = 0x10000000, restored = true))
    }
}
