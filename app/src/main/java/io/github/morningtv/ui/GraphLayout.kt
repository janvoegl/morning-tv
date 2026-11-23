package io.github.morningtv.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Hour range shown on the time graph. [endHour] is exclusive: the last column is endHour - 1. */
data class GraphRange(val firstHour: Int, val endHour: Int)

/**
 * The graph starts one hour before now (or the first appointment, if earlier) and runs to 22:00,
 * or to the end of the last appointment if that is later.
 */
fun graphRange(nowMin: Int, events: List<TimelineEvent>): GraphRange {
    val first = min(nowMin / 60 - 1, events.minOfOrNull { it.startMin / 60 } ?: Int.MAX_VALUE).coerceIn(0, 20)
    val lastEnd = events.maxOfOrNull { ceil(it.endMin / 60.0).toInt() } ?: 0
    return GraphRange(first, min(24, max(22, lastEnd)))
}

/** An appointment chip waiting for a place: its width is measured by the view. */
data class ChipRequest(val index: Int, val startMin: Int, val chipWidth: Float)

/** Where a chip goes: [left] in px from the graph's left edge, [lane] 0..maxLanes-1. */
data class ChipPlacement(val index: Int, val left: Float, val lane: Int)

data class ChipLayout(val placed: List<ChipPlacement>, val hidden: Int)

/**
 * Greedy lane assignment: chips are anchored at the start of their appointment (shifted left if they
 * would leave the graph) and go into the first lane where they do not touch the previous chip.
 * Chips that need more than [maxLanes] lanes are counted as hidden.
 */
fun layoutChips(
    chips: List<ChipRequest>,
    xOfMinute: (Int) -> Float,
    graphWidth: Float,
    gap: Float,
    maxLanes: Int = 3,
): ChipLayout {
    val laneEnd = mutableListOf<Float>()
    val placed = mutableListOf<ChipPlacement>()
    var hidden = 0
    for (c in chips.sortedBy { it.startMin }) {
        val left = max(0f, min(xOfMinute(c.startMin), graphWidth - c.chipWidth))
        var lane = laneEnd.indexOfFirst { left >= it + gap }
        if (lane < 0) lane = laneEnd.size
        if (lane >= maxLanes) { hidden++; continue }
        if (lane == laneEnd.size) laneEnd += 0f
        laneEnd[lane] = left + c.chipWidth
        placed += ChipPlacement(c.index, left, lane)
    }
    return ChipLayout(placed, hidden)
}

/** Horizontal position of [minute] in a graph of [width] px covering [range]. */
fun graphX(minute: Float, range: GraphRange, width: Float): Float =
    (minute - range.firstHour * 60f) / ((range.endHour - range.firstHour) * 60f) * width

/** Hour index helper: the column an appointment starts in, clamped to the range. */
fun hourOf(minute: Int, range: GraphRange): Int = floor(minute / 60f).toInt().coerceIn(range.firstHour, range.endHour - 1)
