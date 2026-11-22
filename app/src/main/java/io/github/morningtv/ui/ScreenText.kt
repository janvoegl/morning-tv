package io.github.morningtv.ui

import io.github.morningtv.calendar.Agenda

/** One appointment row: left column (time / "all day") and title. */
data class AgendaRow(val time: String, val title: String)

const val MAX_TIMED_ROWS = 8

fun greeting(name: String): String = if (name.isBlank()) "Good morning" else "Good morning, ${name.trim()}"

/** Rows for the appointment column. A null agenda means the app was opened by hand. */
fun agendaRows(agenda: Agenda?): List<AgendaRow> {
    if (agenda == null) return listOf(AgendaRow("", "Sent with your alarm"))
    if (agenda.timed.isEmpty() && agenda.allDay.isEmpty()) return listOf(AgendaRow("", "No appointments today"))
    val rows = agenda.timed.take(MAX_TIMED_ROWS).map { AgendaRow(it.timeLabel, it.title) }.toMutableList()
    if (agenda.timed.size > MAX_TIMED_ROWS) rows += AgendaRow("", "+${agenda.timed.size - MAX_TIMED_ROWS} more")
    rows += agenda.allDay.map { AgendaRow("all day", it.title) }
    return rows
}

/** Rain line under the temperatures. Built without String.format: "%" is part of the text. */
fun rainLine(w: io.github.morningtv.weather.WeatherSummary): String = when {
    w.rainFromHour != null -> "Rain ${w.rainChance}% from ${w.rainFromHour.toString().padStart(2, '0')}:00"
    w.rainChance > 0 -> "Rain ${w.rainChance}%"
    else -> "No rain expected"
}
