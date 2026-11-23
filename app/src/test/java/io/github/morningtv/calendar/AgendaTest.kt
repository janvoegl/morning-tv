package io.github.morningtv.calendar

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaTest {
    private val vienna = ZoneId.of("Europe/Vienna")
    private val today = LocalDate.of(2026, 11, 23)

    private fun ev(title: String, start: String, end: String, allDay: Boolean = false) =
        """{"title":"$title","start":"$start","end":"$end","allDay":$allDay}"""

    private fun payload(vararg events: String) = """{"events":[${events.joinToString(",")}]}"""

    @Test
    fun parsesSingleEventInLocalTime() {
        val a = parseAgenda(payload(ev("Team meeting", "2026-11-23T09:00:00Z", "2026-11-23T10:00:00Z")), today, vienna)!!
        assertEquals(listOf(AgendaItem("Team meeting", "10:00", false, 600, 660)), a.timed)
        assertTrue(a.allDay.isEmpty())
    }

    @Test
    fun sortsByStart() {
        val a = parseAgenda(
            payload(
                ev("Gym", "2026-11-23T18:00:00+01:00", "2026-11-23T19:00:00+01:00"),
                ev("Lunch", "2026-11-23T12:30:00+01:00", "2026-11-23T13:30:00+01:00"),
            ), today, vienna
        )!!
        assertEquals(listOf("Lunch", "Gym"), a.timed.map { it.title })
    }

    @Test
    fun allDayGoesToAllDayList() {
        val a = parseAgenda(payload(ev("Birthday", "2026-11-23T00:00:00+01:00", "2026-11-24T00:00:00+01:00", true)), today, vienna)!!
        assertTrue(a.timed.isEmpty())
        assertEquals(listOf(AgendaItem("Birthday", "", true)), a.allDay)
    }

    @Test
    fun eventStartedYesterdayShowsUntil() {
        val a = parseAgenda(payload(ev("Night shift", "2026-11-22T22:00:00+01:00", "2026-11-23T11:00:00+01:00")), today, vienna)!!
        assertEquals(listOf(AgendaItem("Night shift", "until 11:00", false, 0, 660)), a.timed)
    }

    @Test
    fun eventTomorrowDropped() {
        val a = parseAgenda(payload(ev("Tomorrow", "2026-11-24T09:00:00+01:00", "2026-11-24T10:00:00+01:00")), today, vienna)!!
        assertTrue(a.timed.isEmpty())
    }

    @Test
    fun eventEndedYesterdayDropped() {
        val a = parseAgenda(payload(ev("Old", "2026-11-22T09:00:00+01:00", "2026-11-22T10:00:00+01:00")), today, vienna)!!
        assertTrue(a.timed.isEmpty())
    }

    @Test
    fun emptyEventsGivesEmptyAgenda() {
        val a = parseAgenda("""{"events":[]}""", today, vienna)!!
        assertTrue(a.timed.isEmpty() && a.allDay.isEmpty())
    }

    @Test
    fun malformedReturnsNull() {
        assertNull(parseAgenda("{oops", today, vienna))
    }

    @Test
    fun badDateSkipsOnlyThatEvent() {
        val a = parseAgenda(
            payload(ev("Bad", "yesterday-ish", "later"), ev("Good", "2026-11-23T08:00:00+01:00", "2026-11-23T09:00:00+01:00")),
            today, vienna
        )!!
        assertEquals(listOf("Good"), a.timed.map { it.title })
    }

    @Test
    fun blankTitleBecomesNoTitle() {
        val a = parseAgenda(payload(ev("   ", "2026-11-23T08:00:00+01:00", "2026-11-23T09:00:00+01:00")), today, vienna)!!
        assertEquals("(no title)", a.timed.single().title)
    }

    @Test
    fun unknownFieldsIgnored() {
        val a = parseAgenda(
            """{"events":[{"title":"X","start":"2026-11-23T08:00:00+01:00","end":"2026-11-23T09:00:00+01:00","allDay":false,"location":"Room 1"}],"extra":1}""",
            today, vienna
        )!!
        assertEquals(1, a.timed.size)
    }

    @Test
    fun allDayAcceptsShortcutsStyleStrings() {
        val a = parseAgenda(
            """{"events":[{"title":"A","start":"2026-11-23T00:00:00+01:00","end":"2026-11-24T00:00:00+01:00","allDay":"Yes"},""" +
                """{"title":"B","start":"2026-11-23T08:00:00+01:00","end":"2026-11-23T09:00:00+01:00","allDay":"No"},""" +
                """{"title":"C","start":"2026-11-23T00:00:00+01:00","end":"2026-11-24T00:00:00+01:00","allDay":"1"}]}""",
            today, vienna
        )!!
        assertEquals(listOf("A", "C"), a.allDay.map { it.title })
        assertEquals(listOf("B"), a.timed.map { it.title })
    }

    @Test
    fun eventRunningPastMidnightEndsAtDayEnd() {
        val a = parseAgenda(payload(ev("Late", "2026-11-23T22:00:00+01:00", "2026-11-24T01:00:00+01:00")), today, vienna)!!
        assertEquals(listOf(AgendaItem("Late", "22:00", false, 1320, 1440)), a.timed)
    }
}
