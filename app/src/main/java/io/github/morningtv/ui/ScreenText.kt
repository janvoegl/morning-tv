package io.github.morningtv.ui

import io.github.morningtv.calendar.Agenda
import io.github.morningtv.text.Texts

/** One appointment row: left column (time / "all day") and title. */
data class AgendaRow(val time: String, val title: String)

const val MAX_TIMED_ROWS = 8

fun greeting(name: String, t: Texts = Texts.EN): String =
    if (name.isBlank()) t.goodMorning else "${t.goodMorning}, ${name.trim()}"

/** Rows for the appointment column. A null agenda means the app was opened by hand. */
fun agendaRows(agenda: Agenda?, t: Texts = Texts.EN): List<AgendaRow> {
    if (agenda == null) return listOf(AgendaRow("", t.sentWithAlarm))
    if (agenda.timed.isEmpty() && agenda.allDay.isEmpty()) return listOf(AgendaRow("", t.noAppointments))
    val rows = agenda.timed.take(MAX_TIMED_ROWS).map { AgendaRow(it.timeLabel, it.title) }.toMutableList()
    if (agenda.timed.size > MAX_TIMED_ROWS) rows += AgendaRow("", t.more(agenda.timed.size - MAX_TIMED_ROWS))
    rows += agenda.allDay.map { AgendaRow(t.allDay, it.title) }
    return rows
}

/** Rain line under the temperatures. Built without String.format: "%" is part of the text. */
fun rainLine(w: io.github.morningtv.weather.WeatherSummary, t: Texts = Texts.EN): String = when {
    w.rainFromHour != null -> t.rainFrom(w.rainChance, "${w.rainFromHour.toString().padStart(2, '0')}:00")
    w.rainChance > 0 -> t.rain(w.rainChance)
    else -> t.noRain
}
