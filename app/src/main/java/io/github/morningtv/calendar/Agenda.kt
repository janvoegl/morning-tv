package io.github.morningtv.calendar

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One line in the appointment list. [timeLabel]: "09:00", "until 11:00" or "" (all-day). */
data class AgendaItem(val title: String, val timeLabel: String, val allDay: Boolean)

data class Agenda(val timed: List<AgendaItem>, val allDay: List<AgendaItem>)

@Serializable
private data class Payload(val events: List<PayloadEvent> = emptyList())

@Serializable
private data class PayloadEvent(
    val title: String = "",
    val start: String = "",
    val end: String = "",
    val allDay: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true }
private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Parses the payload the iPhone Shortcut sends. Returns null for malformed JSON.
 * Events that don't touch [today] in [zone] are dropped; events with unreadable dates are skipped.
 */
fun parseAgenda(text: String, today: LocalDate, zone: ZoneId): Agenda? {
    val payload = runCatching { json.decodeFromString<Payload>(text) }.getOrNull() ?: return null
    val dayStart = today.atStartOfDay(zone)
    val dayEnd = today.plusDays(1).atStartOfDay(zone)

    val timed = mutableListOf<Pair<ZonedDateTime, AgendaItem>>()
    val allDay = mutableListOf<AgendaItem>()
    for (e in payload.events) {
        val start = parseTime(e.start, zone) ?: continue
        val end = parseTime(e.end, zone) ?: start
        val touchesToday = start < dayEnd && (end > dayStart || start >= dayStart)
        if (!touchesToday) continue
        val title = e.title.trim().ifEmpty { "(no title)" }
        if (e.allDay) {
            allDay += AgendaItem(title, "", true)
        } else {
            val label = if (start < dayStart) "until ${end.format(HHMM)}" else start.format(HHMM)
            timed += start to AgendaItem(title, label, false)
        }
    }
    return Agenda(timed.sortedBy { it.first }.map { it.second }, allDay)
}

private fun parseTime(s: String, zone: ZoneId): ZonedDateTime? =
    runCatching { OffsetDateTime.parse(s).atZoneSameInstant(zone) }.getOrNull()
