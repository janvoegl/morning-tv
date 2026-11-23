package io.github.morningtv.calendar

import io.github.morningtv.text.Texts
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private const val DAY_MINUTES = 24 * 60

/** Turns iCal occurrences (see [parseIcal]) into today's [Agenda], same rules as [parseAgenda]. */
fun icalAgenda(occurrences: List<IcalOccurrence>, day: LocalDate, zone: ZoneId, t: Texts = Texts.EN): Agenda {
    val dayStart = day.atStartOfDay(zone)
    val dayEnd = day.plusDays(1).atStartOfDay(zone)
    val timed = mutableListOf<Pair<java.time.ZonedDateTime, AgendaItem>>()
    val allDay = mutableListOf<AgendaItem>()
    for (o in occurrences) {
        val title = o.title.trim().ifEmpty { "(no title)" }
        if (o.allDay) {
            allDay += AgendaItem(title, "", true)
            continue
        }
        val label = if (o.start < dayStart) t.until(o.end.format(HHMM)) else o.start.format(HHMM)
        val startMin = if (o.start < dayStart) 0 else o.start.hour * 60 + o.start.minute
        val endMin = if (o.end >= dayEnd) DAY_MINUTES else o.end.hour * 60 + o.end.minute
        timed += o.start to AgendaItem(title, label, false, startMin, endMin)
    }
    return Agenda(timed.sortedBy { it.first }.map { it.second }, allDay)
}

/**
 * Combines the Shortcut's agenda with the Google calendar's. The same appointment (same title, same
 * start) appears only once; the first agenda wins. Timed items are sorted by start.
 */
fun mergeAgendas(first: Agenda?, second: Agenda?): Agenda? {
    if (first == null) return second
    if (second == null) return first
    fun key(i: AgendaItem) = Triple(i.title.trim().lowercase(), i.startMin, i.allDay)
    val seen = mutableSetOf<Triple<String, Int, Boolean>>()
    val timed = (first.timed + second.timed).filter { seen.add(key(it)) }.sortedBy { it.startMin }
    val allDay = (first.allDay + second.allDay).filter { seen.add(key(it)) }
    return Agenda(timed, allDay)
}
