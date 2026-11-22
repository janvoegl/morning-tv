package io.github.morningtv.ui

import io.github.morningtv.calendar.Agenda
import java.time.LocalDate

/** An agenda together with the day it was received for. */
data class DatedAgenda(val date: LocalDate, val agenda: Agenda)

/** The agenda to show today, or null if none was received today (e.g. the app was left open overnight). */
fun agendaForToday(stored: DatedAgenda?, today: LocalDate): Agenda? = stored?.takeIf { it.date == today }?.agenda

private const val FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY = 0x00100000

/**
 * True only for a real new launch. A relaunch from recents or a recreated activity carries
 * the old Shortcut intent and must not re-arm the standby timer.
 */
fun isFreshLaunch(flags: Int, restored: Boolean): Boolean =
    !restored && (flags and FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
