package io.github.morningtv.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Hour range shown on the time graph. [firstHour] counts from today 00:00; [endHour] is exclusive and may
 * exceed 24 (the graph then runs into tomorrow, hour 24 = 00:00 tomorrow).
 */
data class GraphRange(val firstHour: Int, val endHour: Int)

const val GRAPH_MIN_SPAN_HOURS = 12
const val GRAPH_LOOKBACK_HOURS = 4
const val GRAPH_MAX_HOUR = 48

/**
 * The graph starts one hour before now. Appointments that ended within the last [GRAPH_LOOKBACK_HOURS]
 * hours pull the start back (to their start hour, at most that far), so a missed appointment stays visible.
 * It runs at least until 22:00 and at least [GRAPH_MIN_SPAN_HOURS] hours, so it keeps looking ahead
 * (past midnight into tomorrow); later appointments extend it further, up to [GRAPH_MAX_HOUR].
 */
fun graphRange(nowMin: Int, events: List<TimelineEvent>): GraphRange {
    val nowHour = nowMin / 60
    val missed = events.filter { it.endMin <= nowMin && it.endMin > nowMin - GRAPH_LOOKBACK_HOURS * 60 }
    val back = missed.minOfOrNull { it.startMin / 60 }?.coerceAtLeast(nowHour - GRAPH_LOOKBACK_HOURS)
    val first = min(nowHour - 1, back ?: Int.MAX_VALUE).coerceIn(0, 23)
    val lastEnd = events.filter { it.endMin > first * 60 }.maxOfOrNull { ceil(it.endMin / 60.0).toInt() } ?: 0
    return GraphRange(first, min(GRAPH_MAX_HOUR, maxOf(22, first + GRAPH_MIN_SPAN_HOURS, lastEnd)))
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
