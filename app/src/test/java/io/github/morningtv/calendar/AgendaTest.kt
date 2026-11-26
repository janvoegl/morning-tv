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
                """{"title":"C","start":"2026-11-23T00:00:00+01:00","end":"2026-11-24T00:00:00+01:00","allDay":"1"},""" +
                """{"title":"D","start":"2026-11-23T00:00:00+01:00","end":"2026-11-24T00:00:00+01:00","allDay":"Ja"},""" +
                """{"title":"E","start":"2026-11-23T00:00:00+01:00","end":"2026-11-24T00:00:00+01:00","allDay":"ja"},""" +
                """{"title":"F","start":"2026-11-23T08:00:00+01:00","end":"2026-11-23T09:00:00+01:00","allDay":"Nein"},""" +
                """{"title":"G","start":"2026-11-23T08:00:00+01:00","end":"2026-11-23T09:00:00+01:00","allDay":"0"},""" +
                """{"title":"H","start":"2026-11-23T08:00:00+01:00","end":"2026-11-23T09:00:00+01:00","allDay":"false"}]}""",
            today, vienna
        )!!
        assertEquals(listOf("A", "C", "D", "E"), a.allDay.map { it.title })
        assertEquals(listOf("B", "F", "G", "H"), a.timed.map { it.title })
    }

    @Test
    fun eventRunningPastMidnightEndsAtDayEnd() {
        val a = parseAgenda(payload(ev("Late", "2026-11-23T22:00:00+01:00", "2026-11-24T01:00:00+01:00")), today, vienna)!!
        assertEquals(listOf(AgendaItem("Late", "22:00", false, 1320, 1440)), a.timed)
    }

    @Test
    fun eventsAsStringContainingOneJsonObjectParsesToOneEvent() {
        val payload = """{"keepOn":"0","events":"{\"allDay\":\"true\",\"title\":\"Anna Beispiel 🍻 (30. Geburtstag)\",\"end\":\"2026-11-26T23:59:59+01:00\",\"start\":\"2026-11-26T00:00:00+01:00\"}"}"""
        val a = parseAgenda(payload, LocalDate.of(2026, 11, 26), vienna)!!
        assertEquals(listOf(AgendaItem("Anna Beispiel 🍻 (30. Geburtstag)", "", true)), a.allDay)
        assertTrue(a.timed.isEmpty())
    }

    @Test
    fun eventsAsStringContainingJsonArrayParsesLikeRealArray() {
        val payload = """{"events":"[{\"title\":\"Gym\",\"start\":\"2026-11-23T18:00:00+01:00\",\"end\":\"2026-11-23T19:00:00+01:00\"},{\"title\":\"Lunch\",\"start\":\"2026-11-23T12:30:00+01:00\",\"end\":\"2026-11-23T13:30:00+01:00\"}]"}"""
        val a = parseAgenda(payload, today, vienna)!!
        assertEquals(listOf("Lunch", "Gym"), a.timed.map { it.title })
        assertTrue(a.allDay.isEmpty())
    }

    @Test
    fun eventsAsStringContainingSeveralTopLevelObjectsSeparatedByNewlinesAndCommas() {
        val ev1 = """{"title":"Gym {cardio}","start":"2026-11-23T18:00:00+01:00","end":"2026-11-23T19:00:00+01:00"}"""
        val ev2 = """{"title":"Say \"Hello\"","start":"2026-11-23T12:30:00+01:00","end":"2026-11-23T13:30:00+01:00"}"""
        val escapedEv1 = kotlinx.serialization.json.JsonPrimitive(ev1).toString().removeSurrounding("\"")
        val escapedEv2 = kotlinx.serialization.json.JsonPrimitive(ev2).toString().removeSurrounding("\"")

        // Newline separated
        val payloadNewline = """{"events":"$escapedEv1\n$escapedEv2"}"""
        val a1 = parseAgenda(payloadNewline, today, vienna)!!
        assertEquals(listOf("Say \"Hello\"", "Gym {cardio}"), a1.timed.map { it.title })

        // Comma separated
        val payloadComma = """{"events":"$escapedEv1,$escapedEv2"}"""
        val a2 = parseAgenda(payloadComma, today, vienna)!!
        assertEquals(listOf("Say \"Hello\"", "Gym {cardio}"), a2.timed.map { it.title })

        // Comma and newline separated
        val payloadBoth = """{"events":"$escapedEv1,\n$escapedEv2"}"""
        val a3 = parseAgenda(payloadBoth, today, vienna)!!
        assertEquals(listOf("Say \"Hello\"", "Gym {cardio}"), a3.timed.map { it.title })
    }

    @Test
    fun eventsAsEmptyStringOrEmptyArrayStringGivesEmptyAgenda() {
        val a1 = parseAgenda("""{"events":""}""", today, vienna)!!
        assertTrue(a1.timed.isEmpty() && a1.allDay.isEmpty())

        val a2 = parseAgenda("""{"events":"   "}""", today, vienna)!!
        assertTrue(a2.timed.isEmpty() && a2.allDay.isEmpty())

        val a3 = parseAgenda("""{"events":"[]"}""", today, vienna)!!
        assertTrue(a3.timed.isEmpty() && a3.allDay.isEmpty())

        val a4 = parseAgenda("""{"events":" [ ] "}""", today, vienna)!!
        assertTrue(a4.timed.isEmpty() && a4.allDay.isEmpty())
    }

    @Test
    fun eventsAsSingleObjectCountsAsOneEvent() {
        val a = parseAgenda(
            """{"events":{"title":"Birthday","start":"2026-11-23T00:00:00+01:00","end":"2026-11-23T23:59:59+01:00","allDay":"true"}}""",
            today, vienna,
        )!!
        assertEquals(listOf(AgendaItem("Birthday", "", true)), a.allDay)
    }
}
