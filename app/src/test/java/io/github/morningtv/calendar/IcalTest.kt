package io.github.morningtv.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class IcalTest {

    private val day = LocalDate.of(2026, 11, 23)
    private val zone = ZoneId.of("Europe/Vienna")

    @Test
    fun testSingleUtcEvent() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:utc-1
            DTSTART:20261123T090000Z
            DTEND:20261123T100000Z
            SUMMARY:UTC Meeting
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        val occ = results[0]
        assertEquals("UTC Meeting", occ.title)
        assertFalse(occ.allDay)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, zone), occ.start)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 11, 0, 0, 0, zone), occ.end)
    }

    @Test
    fun testTzidEvent() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:tzid-1
            DTSTART;TZID=Europe/Vienna:20261123T090000
            DTEND;TZID=Europe/Vienna:20261123T100000
            SUMMARY:Vienna Meeting
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        val occ = results[0]
        assertEquals("Vienna Meeting", occ.title)
        assertFalse(occ.allDay)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 9, 0, 0, 0, zone), occ.start)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, zone), occ.end)
    }

    @Test
    fun testFloatingEvent() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:float-1
            DTSTART:20261123T090000
            DTEND:20261123T100000
            SUMMARY:Floating Meeting
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        val occ = results[0]
        assertEquals("Floating Meeting", occ.title)
        assertFalse(occ.allDay)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 9, 0, 0, 0, zone), occ.start)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, zone), occ.end)
    }

    @Test
    fun testFoldedLongSummaryAndEscapedCharacters() {
        val ics = "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "BEGIN:VEVENT\r\n" +
                "UID:folded-1\r\n" +
                "DTSTART:20261123T090000Z\r\n" +
                "DTEND:20261123T100000Z\r\n" +
                "SUMMARY:Project Kickoff\\,\r\n" +
                "  Planning\\, and Review\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR"

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        assertEquals("Project Kickoff, Planning, and Review", results[0].title)
    }

    @Test
    fun testAllDaySingleDay() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:allday-1
            DTSTART;VALUE=DATE:20261123
            DTEND;VALUE=DATE:20261124
            SUMMARY:All Day Single
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        val occ = results[0]
        assertEquals("All Day Single", occ.title)
        assertTrue(occ.allDay)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 0, 0, 0, 0, zone), occ.start)
        assertEquals(ZonedDateTime.of(2026, 11, 24, 0, 0, 0, 0, zone), occ.end)
    }

    @Test
    fun testAllDayMultiDayCoveringToday() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:allday-multi
            DTSTART;VALUE=DATE:20261120
            DTEND;VALUE=DATE:20261125
            SUMMARY:Conference
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        val occ = results[0]
        assertEquals("Conference", occ.title)
        assertTrue(occ.allDay)
        assertEquals(ZonedDateTime.of(2026, 11, 20, 0, 0, 0, 0, zone), occ.start)
        assertEquals(ZonedDateTime.of(2026, 11, 25, 0, 0, 0, 0, zone), occ.end)
    }

    @Test
    fun testAllDayEndingTodayExclusiveDtendNotShown() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:allday-ending
            DTSTART;VALUE=DATE:20261120
            DTEND;VALUE=DATE:20261123
            SUMMARY:Trip
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(0, results.size)
    }

    @Test
    fun testWeeklyBydayMoWeStartedWeeksAgoShowsOnMonday() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:weekly-mowe
            DTSTART:20261026T090000Z
            DTEND:20261026T100000Z
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE
            SUMMARY:Biweekly Sync
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        assertEquals("Biweekly Sync", results[0].title)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, zone), results[0].start)
    }

    @Test
    fun testWeeklyInterval2AlternateWeeks() {
        // Starts 2026-11-09 (2 weeks before Nov 23): should show on Nov 23
        val icsMatching = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:biweekly-1
            DTSTART:20261109T090000Z
            DTEND:20261109T100000Z
            RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO
            SUMMARY:Fortnightly Matching
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val resultsMatching = parseIcal(icsMatching, day, zone)
        assertEquals(1, resultsMatching.size)
        assertEquals("Fortnightly Matching", resultsMatching[0].title)

        // Starts 2026-11-16 (1 week before Nov 23): should skip Nov 23
        val icsSkipped = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:biweekly-2
            DTSTART:20261116T090000Z
            DTEND:20261116T100000Z
            RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO
            SUMMARY:Fortnightly Skipped
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val resultsSkipped = parseIcal(icsSkipped, day, zone)
        assertEquals(0, resultsSkipped.size)
    }

    @Test
    fun testWeeklyCountReachedNotShown() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:weekly-count
            DTSTART:20261109T090000Z
            DTEND:20261109T100000Z
            RRULE:FREQ=WEEKLY;COUNT=2;BYDAY=MO,WE
            SUMMARY:Sprint Kickoffs
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        // Count=2 means Nov 9 (MO) and Nov 11 (WE). Nov 23 is beyond COUNT.
        val results = parseIcal(ics, day, zone)
        assertEquals(0, results.size)
    }

    @Test
    fun testWeeklyUntilPassedNotShown() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:weekly-until
            DTSTART:20261026T090000Z
            DTEND:20261026T100000Z
            RRULE:FREQ=WEEKLY;UNTIL=20261120T235959Z;BYDAY=MO
            SUMMARY:Limited Series
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(0, results.size)
    }

    @Test
    fun testDailyRecurrence() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:daily-1
            DTSTART:20261101T080000Z
            DTEND:20261101T083000Z
            RRULE:FREQ=DAILY
            SUMMARY:Daily Standup
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        assertEquals("Daily Standup", results[0].title)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 9, 0, 0, 0, zone), results[0].start)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 9, 30, 0, 0, zone), results[0].end)
    }

    @Test
    fun testMonthlyByMonthDay23() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:monthly-bymonthday
            DTSTART:20260923T100000Z
            DTEND:20260923T110000Z
            RRULE:FREQ=MONTHLY;BYMONTHDAY=23
            SUMMARY:Monthly Report
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        assertEquals("Monthly Report", results[0].title)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 11, 0, 0, 0, zone), results[0].start)
    }

    @Test
    fun testMonthly4MoFourthMonday() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:monthly-4mo
            DTSTART:20260928T100000Z
            DTEND:20260928T110000Z
            RRULE:FREQ=MONTHLY;BYDAY=4MO
            SUMMARY:Board Meeting
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        // In Nov 2026, Mondays are Nov 2, 9, 16, 23, 30. 4th Monday is Nov 23!
        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        assertEquals("Board Meeting", results[0].title)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 11, 0, 0, 0, zone), results[0].start)
    }

    @Test
    fun testYearlyRecurrence() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:yearly-1
            DTSTART:20241123T100000Z
            DTEND:20241123T110000Z
            RRULE:FREQ=YEARLY
            SUMMARY:Annual Checkup
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        assertEquals("Annual Checkup", results[0].title)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 11, 0, 0, 0, zone), results[0].start)
    }

    @Test
    fun testExdateRemovesTodaysInstance() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:weekly-exdate
            DTSTART:20261109T090000Z
            DTEND:20261109T100000Z
            RRULE:FREQ=WEEKLY;BYDAY=MO
            EXDATE:20261123T090000Z
            SUMMARY:Weekly Team Sync
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(0, results.size)
    }

    @Test
    fun testRecurrenceIdOverrideMovesInstanceAwayFromToday() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:recurring-move-away
            DTSTART:20261109T090000Z
            DTEND:20261109T100000Z
            RRULE:FREQ=WEEKLY;BYDAY=MO
            SUMMARY:Regular Standup
            END:VEVENT
            BEGIN:VEVENT
            UID:recurring-move-away
            RECURRENCE-ID:20261123T090000Z
            DTSTART:20261124T090000Z
            DTEND:20261124T100000Z
            SUMMARY:Moved to Tuesday
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(0, results.size)
    }

    @Test
    fun testRecurrenceIdOverrideMovesInstanceToToday() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:recurring-move-to-today
            DTSTART:20261108T090000Z
            DTEND:20261108T100000Z
            RRULE:FREQ=WEEKLY;BYDAY=SU
            SUMMARY:Sunday Review
            END:VEVENT
            BEGIN:VEVENT
            UID:recurring-move-to-today
            RECURRENCE-ID:20261122T090000Z
            DTSTART:20261123T140000Z
            DTEND:20261123T150000Z
            SUMMARY:Review Moved To Monday
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        assertEquals("Review Moved To Monday", results[0].title)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 15, 0, 0, 0, zone), results[0].start)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 16, 0, 0, 0, zone), results[0].end)
    }

    @Test
    fun testCancelledEventSkipped() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:cancelled-standalone
            DTSTART:20261123T090000Z
            DTEND:20261123T100000Z
            STATUS:CANCELLED
            SUMMARY:Cancelled Meeting
            END:VEVENT
            BEGIN:VEVENT
            UID:cancelled-override-series
            DTSTART:20261109T090000Z
            DTEND:20261109T100000Z
            RRULE:FREQ=WEEKLY;BYDAY=MO
            SUMMARY:Weekly Sync
            END:VEVENT
            BEGIN:VEVENT
            UID:cancelled-override-series
            RECURRENCE-ID:20261123T090000Z
            STATUS:CANCELLED
            SUMMARY:Weekly Sync
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(0, results.size)
    }

    @Test
    fun testDstWeekly0900EuropeViennaStartedInSummerShowsAt0900LocalToday() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:dst-summer-start
            DTSTART;TZID=Europe/Vienna:20260706T090000
            DTEND;TZID=Europe/Vienna:20260706T100000
            RRULE:FREQ=WEEKLY;BYDAY=MO
            SUMMARY:Monday Coffee
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        // In July, Europe/Vienna is UTC+2 (CEST).
        // On 2026-11-23, Europe/Vienna is UTC+1 (CET).
        // Wall clock time must remain 09:00 local time!
        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        assertEquals("Monday Coffee", results[0].title)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 9, 0, 0, 0, zone), results[0].start)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, zone), results[0].end)
    }

    @Test
    fun testEventStartedYesterdayAndEndsTodayReturnsWithRealStartAndEnd() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:overnight-1
            DTSTART;TZID=Europe/Vienna:20261122T220000
            DTEND;TZID=Europe/Vienna:20261123T020000
            SUMMARY:Overnight Shift
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        val occ = results[0]
        assertEquals("Overnight Shift", occ.title)
        assertEquals(ZonedDateTime.of(2026, 11, 22, 22, 0, 0, 0, zone), occ.start)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 2, 0, 0, 0, zone), occ.end)
    }

    @Test
    fun testGarbageInputReturnsEmptyListNoException() {
        assertEquals(emptyList<IcalOccurrence>(), parseIcal("totally corrupt ical data", day, zone))
        assertEquals(emptyList<IcalOccurrence>(), parseIcal("", day, zone))
        assertEquals(
            emptyList<IcalOccurrence>(),
            parseIcal(
                """
                BEGIN:VEVENT
                DTSTART:invalid-date
                END:VEVENT
                """.trimIndent(),
                day,
                zone
            )
        )
    }

    @Test
    fun testUnsupportedRrulePartsFallbackToDtstartInstance() {
        // DTSTART is today; RRULE has unsupported BYSETPOS -> treated as non-recurring DTSTART instance -> shown today
        val icsToday = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:unsupported-today
            DTSTART;TZID=Europe/Vienna:20261123T110000
            DTEND;TZID=Europe/Vienna:20261123T120000
            RRULE:FREQ=WEEKLY;BYSETPOS=1;BYDAY=MO,WE
            SUMMARY:Fallback Today
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val resultsToday = parseIcal(icsToday, day, zone)
        assertEquals(1, resultsToday.size)
        assertEquals("Fallback Today", resultsToday[0].title)

        // DTSTART was last week; RRULE has unsupported BYSETPOS -> treated as non-recurring -> NOT shown today
        val icsLastWeek = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:unsupported-last-week
            DTSTART;TZID=Europe/Vienna:20261116T110000
            DTEND;TZID=Europe/Vienna:20261116T120000
            RRULE:FREQ=WEEKLY;BYSETPOS=1;BYDAY=MO,WE
            SUMMARY:Fallback Last Week
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val resultsLastWeek = parseIcal(icsLastWeek, day, zone)
        assertEquals(0, resultsLastWeek.size)
    }

    @Test
    fun testEmptySummaryDefaultsToNoTitle() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:empty-title
            DTSTART:20261123T090000Z
            DTEND:20261123T100000Z
            SUMMARY:
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
        assertEquals("(no title)", results[0].title)
    }

    @Test
    fun testDeduplicationSameUidAndStart() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:dup-1
            DTSTART:20261123T090000Z
            DTEND:20261123T100000Z
            SUMMARY:Dup Event First
            END:VEVENT
            BEGIN:VEVENT
            UID:dup-1
            DTSTART:20261123T090000Z
            DTEND:20261123T100000Z
            SUMMARY:Dup Event Second
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val results = parseIcal(ics, day, zone)
        assertEquals(1, results.size)
    }

    @Test
    fun testNoDtendDefaults() {
        // Timed: end = start
        val icsTimed = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:no-dtend-timed
            DTSTART:20261123T090000Z
            SUMMARY:Zero Duration
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val resTimed = parseIcal(icsTimed, day, zone)
        assertEquals(1, resTimed.size)
        assertEquals(resTimed[0].start, resTimed[0].end)

        // All-day: end = start + 1 day
        val icsAllDay = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:no-dtend-allday
            DTSTART;VALUE=DATE:20261123
            SUMMARY:All Day Single No Dtend
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val resAllDay = parseIcal(icsAllDay, day, zone)
        assertEquals(1, resAllDay.size)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 0, 0, 0, 0, zone), resAllDay[0].start)
        assertEquals(ZonedDateTime.of(2026, 11, 24, 0, 0, 0, 0, zone), resAllDay[0].end)
    }
}
