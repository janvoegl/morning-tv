package io.github.morningtv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineTest {

    @Test
    fun emptyInputReturnsNull() {
        assertNull(buildTimeline(emptyList()))
    }

    @Test
    fun singleEventNearMidnightClampsFirstHourToZero() {
        val timeline = buildTimeline(listOf(TimelineEvent(startMin = 10, endMin = 40, index = 0)))!!
        assertEquals(0, timeline.firstHour)
        assertEquals(1, timeline.endHour)
        assertEquals(1, timeline.rowsPerColumn)
        assertEquals(0, timeline.hidden)
        assertEquals(1, timeline.blocks.size)
        val block = timeline.blocks[0]
        assertEquals(0, block.index)
        assertEquals(0, block.column)
        assertEquals(10 / 60f, block.topHours, 0.001f)
        assertEquals(30 / 60f, block.heightHours, 0.001f)
    }

    @Test
    fun zeroLengthEventGetsThirtyMinutes() {
        val timeline = buildTimeline(listOf(TimelineEvent(startMin = 540, endMin = 540, index = 0)))!!
        assertEquals(8, timeline.firstHour)
        assertEquals(10, timeline.endHour)
        assertEquals(1, timeline.rowsPerColumn)
        assertEquals(1, timeline.blocks.size)
        val block = timeline.blocks[0]
        assertEquals(0, block.index)
        assertEquals(1, block.column)
        assertEquals(0.0f, block.topHours, 0.001f)
        assertEquals(0.5f, block.heightHours, 0.001f)
    }

    @Test
    fun negativeLengthEventGetsThirtyMinutes() {
        val timeline = buildTimeline(listOf(TimelineEvent(startMin = 540, endMin = 500, index = 0)))!!
        assertEquals(8, timeline.firstHour)
        assertEquals(10, timeline.endHour)
        assertEquals(1, timeline.rowsPerColumn)
        assertEquals(1, timeline.blocks.size)
        val block = timeline.blocks[0]
        assertEquals(0, block.index)
        assertEquals(1, block.column)
        assertEquals(0.0f, block.topHours, 0.001f)
        assertEquals(0.5f, block.heightHours, 0.001f)
    }

    @Test
    fun multiEventLayoutWithSplitBlock() {
        val events = listOf(
            TimelineEvent(startMin = 540, endMin = 570, index = 0),
            TimelineEvent(startMin = 690, endMin = 735, index = 1),
            TimelineEvent(startMin = 840, endMin = 930, index = 2),
        )
        val timeline = buildTimeline(events)!!
        assertEquals(8, timeline.firstHour)
        assertEquals(16, timeline.endHour)
        assertEquals(4, timeline.rowsPerColumn)
        assertEquals(0, timeline.hidden)
        assertEquals(4, timeline.blocks.size)

        // Blocks ordered by column, then topHours:
        // Column 0: Event 0 (top 1.0, height 0.5), Event 1 (top 3.5, height 0.5)
        // Column 1: Event 1 (top 0.0, height 0.25), Event 2 (top 2.0, height 1.5)
        val b0 = timeline.blocks[0]
        assertEquals(0, b0.index)
        assertEquals(0, b0.column)
        assertEquals(1.0f, b0.topHours, 0.001f)
        assertEquals(0.5f, b0.heightHours, 0.001f)

        val b1 = timeline.blocks[1]
        assertEquals(1, b1.index)
        assertEquals(0, b1.column)
        assertEquals(3.5f, b1.topHours, 0.001f)
        assertEquals(0.5f, b1.heightHours, 0.001f)

        val b2 = timeline.blocks[2]
        assertEquals(1, b2.index)
        assertEquals(1, b2.column)
        assertEquals(0.0f, b2.topHours, 0.001f)
        assertEquals(0.25f, b2.heightHours, 0.001f)

        val b3 = timeline.blocks[3]
        assertEquals(2, b3.index)
        assertEquals(1, b3.column)
        assertEquals(2.0f, b3.topHours, 0.001f)
        assertEquals(1.5f, b3.heightHours, 0.001f)
    }

    @Test
    fun maxHoursClippingWithHiddenEvents() {
        // Events at 06:00 (360..420) and 22:00 (1320..1380) with maxHours 12 -> 22:00 is hidden
        val events = listOf(
            TimelineEvent(startMin = 360, endMin = 420, index = 0),
            TimelineEvent(startMin = 1320, endMin = 1380, index = 1),
        )
        val timeline = buildTimeline(events, maxHours = 12)!!
        assertEquals(5, timeline.firstHour)
        assertEquals(17, timeline.endHour)
        assertEquals(6, timeline.rowsPerColumn)
        assertEquals(1, timeline.hidden)
        assertEquals(1, timeline.blocks.size)

        val b0 = timeline.blocks[0]
        assertEquals(0, b0.index)
        assertEquals(0, b0.column)
        assertEquals(1.0f, b0.topHours, 0.001f)
        assertEquals(1.0f, b0.heightHours, 0.001f)
    }

    @Test
    fun maxHoursClippingCutsOverlappingEvent() {
        // Event starts at 16:30 (990) and ends at 17:30 (1050), window ends at 17:00 (1020)
        val events = listOf(
            TimelineEvent(startMin = 360, endMin = 420, index = 0),
            TimelineEvent(startMin = 990, endMin = 1050, index = 1),
        )
        val timeline = buildTimeline(events, maxHours = 12)!!
        assertEquals(5, timeline.firstHour)
        assertEquals(17, timeline.endHour)
        assertEquals(0, timeline.hidden)
        assertEquals(2, timeline.blocks.size)

        val b1 = timeline.blocks[1]
        assertEquals(1, b1.index)
        assertEquals(1, b1.column)
        // column 1 starts at hour 5 + 6 = 11 (660 min). 990 - 660 = 330 min -> 5.5 hours
        assertEquals(5.5f, b1.topHours, 0.001f)
        // clipped at 1020 min (17:00): 1020 - 990 = 30 min -> 0.5 hours
        assertEquals(0.5f, b1.heightHours, 0.001f)
    }
}
