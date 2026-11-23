package io.github.morningtv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphLayoutTest {
    private fun ev(s: Int, e: Int, i: Int = 0) = TimelineEvent(s, e, i)

    @Test
    fun rangeStartsOneHourBeforeNowAndEndsAt22() {
        assertEquals(GraphRange(6, 22), graphRange(7 * 60 + 20, emptyList()))
    }

    @Test
    fun rangeStartsAtEarlierAppointment() {
        assertEquals(GraphRange(4, 22), graphRange(7 * 60, listOf(ev(4 * 60 + 30, 5 * 60))))
    }

    @Test
    fun rangeExtendsForLateAppointmentUpTo24() {
        assertEquals(GraphRange(6, 24), graphRange(7 * 60, listOf(ev(22 * 60, 23 * 60 + 30))))
        assertEquals(GraphRange(6, 23), graphRange(7 * 60, listOf(ev(21 * 60, 22 * 60 + 5))))
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
