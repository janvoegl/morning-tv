package io.github.morningtv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphLayoutTest {
    private fun ev(s: Int, e: Int, i: Int = 0) = TimelineEvent(s, e, i)

    @Test
    fun morningRangeIsOneHourBackUntil22() {
        assertEquals(GraphRange(6, 22), graphRange(7 * 60 + 20, emptyList()))
    }

    @Test
    fun eveningRangeKeepsLookingTwelveHoursAheadIntoTomorrow() {
        assertEquals(GraphRange(19, 31), graphRange(20 * 60 + 5, emptyList()))
        assertEquals(GraphRange(21, 33), graphRange(22 * 60 + 30, emptyList()))
        assertEquals(GraphRange(22, 34), graphRange(23 * 60 + 59, emptyList()))
    }

    @Test
    fun afterMidnightStartsAtZero() {
        assertEquals(GraphRange(0, 22), graphRange(30, emptyList()))
    }

    @Test
    fun recentlyEndedAppointmentPullsTheStartBack() {
        // now 14:20; appointment 11:00-12:00 ended 2h20m ago -> start 11
        assertEquals(GraphRange(11, 23), graphRange(14 * 60 + 20, listOf(ev(11 * 60, 12 * 60))))
        // ended 5h ago: not shown any more
        assertEquals(GraphRange(13, 25), graphRange(14 * 60 + 20, listOf(ev(8 * 60, 9 * 60))))
    }

    @Test
    fun missedAppointmentIsLookedBackAtMostFourHours() {
        // started 09:00, ended 10:30, now 13:00: ends 2.5 h ago, but its start (9) is 4 h back -> 9
        assertEquals(9, graphRange(13 * 60, listOf(ev(9 * 60, 10 * 60 + 30))).firstHour)
        // started 08:00, ended 10:30, now 13:00: start would be 5 h back -> limited to 9
        assertEquals(9, graphRange(13 * 60, listOf(ev(8 * 60, 10 * 60 + 30))).firstHour)
    }

    @Test
    fun lateAppointmentExtendsTheEnd() {
        assertEquals(24, graphRange(7 * 60, listOf(ev(22 * 60, 23 * 60 + 30))).endHour)
        assertEquals(23, graphRange(7 * 60, listOf(ev(21 * 60, 22 * 60 + 5))).endHour)
    }

    @Test
    fun graphXMapsMinutesLinearly() {
        val r = GraphRange(6, 22)
        assertEquals(0f, graphX(360f, r, 1600f), 0.01f)
        assertEquals(100f, graphX(420f, r, 1600f), 0.01f)
        assertEquals(1600f, graphX(1320f, r, 1600f), 0.01f)
    }

    private fun x(min: Int) = min / 60f * 100f  // 100 px per hour

    @Test
    fun separateChipsShareLane0() {
        val l = layoutChips(listOf(ChipRequest(0, 540, 150f), ChipRequest(1, 840, 150f)), ::x, 2400f, 6f)
        assertEquals(listOf(0, 0), l.placed.map { it.lane })
        assertEquals(0, l.hidden)
    }

    @Test
    fun overlappingChipsGoToNextLane() {
        val l = layoutChips(listOf(ChipRequest(0, 540, 150f), ChipRequest(1, 570, 150f)), ::x, 2400f, 6f)
        assertEquals(listOf(0, 1), l.placed.map { it.lane })
    }

    @Test
    fun chipNearRightEdgeIsShiftedLeft() {
        val l = layoutChips(listOf(ChipRequest(0, 1300, 200f)), ::x, 2200f, 6f)
        assertEquals(2000f, l.placed.single().left, 0.01f)
    }

    @Test
    fun fourthLaneIsHiddenAndCounted() {
        val chips = (0..3).map { ChipRequest(it, 540 + it * 5, 150f) }
        val l = layoutChips(chips, ::x, 2400f, 6f)
        assertEquals(3, l.placed.size)
        assertEquals(1, l.hidden)
    }
}
