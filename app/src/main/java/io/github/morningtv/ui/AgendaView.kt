package io.github.morningtv.ui

import io.github.morningtv.calendar.Agenda
import io.github.morningtv.calendar.AgendaItem

/** What the calendar area of the screen shows. */
sealed interface AgendaView {
    /** Two or more appointments: the large timeline card. [stamp] is set when the data is a cached copy. */
    data class Card(val agenda: Agenda, val stamp: String?) : AgendaView

    /** Exactly one appointment: the slim widget. */
    data class Single(val item: AgendaItem, val stamp: String?) : AgendaView

    /** Nothing to list: the slim widget with a message. */
    data class Message(val kind: Kind) : AgendaView

    enum class Kind { NO_APPOINTMENTS, WAITING_FOR_ALARM }
}

/**
 * Chooses the calendar view. [agenda] null = no data for today (app opened by hand, nothing cached).
 * [stamp] = "HH:mm" the data arrived at, only for data that is not from this launch.
 */
fun agendaView(agenda: Agenda?, stamp: String? = null): AgendaView {
    if (agenda == null) return AgendaView.Message(AgendaView.Kind.WAITING_FOR_ALARM)
    val all = agenda.timed + agenda.allDay
    return when (all.size) {
        0 -> AgendaView.Message(AgendaView.Kind.NO_APPOINTMENTS)
        1 -> AgendaView.Single(all.single(), stamp)
        else -> AgendaView.Card(agenda, stamp)
    }
}
