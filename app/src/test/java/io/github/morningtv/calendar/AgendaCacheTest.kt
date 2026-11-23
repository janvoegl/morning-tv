package io.github.morningtv.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgendaCacheTest {
    private val today = LocalDate.of(2026, 11, 23)
    private val agenda = Agenda(
        listOf(AgendaItem("Dentist", "11:30", false, 690, 735)),
        listOf(AgendaItem("Birthday", "", true)),
    )

    @Test
    fun roundTripsToday() {
        val c = decodeCache(encodeCache(today, "06:45", agenda), today)!!
        assertEquals(agenda, c.agenda)
        assertEquals("06:45", c.receivedAt)
    }

    @Test
    fun staleCacheIsDropped() {
        assertNull(decodeCache(encodeCache(today.minusDays(1), "06:45", agenda), today))
    }

    @Test
    fun missingOrBrokenIsNull() {
        assertNull(decodeCache(null, today))
        assertNull(decodeCache("", today))
        assertNull(decodeCache("{oops", today))
    }
}
