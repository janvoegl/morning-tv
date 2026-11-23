package io.github.morningtv.ui

import kotlin.math.ceil

/** An input calendar event defined in minutes since midnight. */
data class TimelineEvent(val startMin: Int, val endMin: Int, val index: Int)

/** A rendered block within a timeline column. */
data class TimelineBlock(val index: Int, val column: Int, val topHours: Float, val heightHours: Float)

/** Layout info for a two-column timeline. */
data class Timeline(
    val firstHour: Int,
    val endHour: Int,
    val rowsPerColumn: Int,
    val blocks: List<TimelineBlock>,
    val hidden: Int,
)

/** Computes the two-column timeline layout for [events] up to [maxHours] duration. */
fun buildTimeline(events: List<TimelineEvent>, maxHours: Int = 12): Timeline? {
    if (events.isEmpty()) return null

    val normalizedEvents = events.map {
        if (it.endMin <= it.startMin) it.copy(endMin = it.startMin + 30) else it
    }

    val minStart = normalizedEvents.minOf { it.startMin }
    val maxEnd = normalizedEvents.maxOf { it.endMin }

    val firstHour = maxOf(0, minStart / 60 - 1)
    var endHour = minOf(24, ceil(maxEnd / 60.0).toInt())

    var hidden = 0
    val windowStart = firstHour * 60
    val isCapped = (endHour - firstHour > maxHours)
    if (isCapped) {
        endHour = firstHour + maxHours
    }
    val windowEnd = endHour * 60

    val clippedEvents = mutableListOf<TimelineEvent>()
    for (event in normalizedEvents) {
        if (event.endMin <= windowStart || event.startMin >= windowEnd) {
            if (isCapped) {
                hidden++
            }
        } else {
            val clippedStart = maxOf(event.startMin, windowStart)
            val clippedEnd = minOf(event.endMin, windowEnd)
            clippedEvents.add(event.copy(startMin = clippedStart, endMin = clippedEnd))
        }
    }

    val rowsPerColumn = ceil((endHour - firstHour) / 2.0).toInt()

    val blocks = mutableListOf<TimelineBlock>()
    for (event in clippedEvents) {
        for (c in 0..1) {
            val columnStartMin = (firstHour + c * rowsPerColumn) * 60
            val columnEndMin = (firstHour + (c + 1) * rowsPerColumn) * 60
            val clippedStart = maxOf(event.startMin, columnStartMin)
            val clippedEnd = minOf(event.endMin, columnEndMin)
            if (clippedStart < clippedEnd) {
                val topHours = (clippedStart - columnStartMin) / 60f
                val heightHours = (clippedEnd - clippedStart) / 60f
                blocks.add(TimelineBlock(index = event.index, column = c, topHours = topHours, heightHours = heightHours))
            }
        }
    }

    val sortedBlocks = blocks.sortedWith(compareBy({ it.column }, { it.topHours }))

    return Timeline(
        firstHour = firstHour,
        endHour = endHour,
        rowsPerColumn = rowsPerColumn,
        blocks = sortedBlocks,
        hidden = hidden,
    )
}
