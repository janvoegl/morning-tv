package io.github.morningtv.calendar

import io.github.morningtv.text.Texts
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * One appointment. [timeLabel]: "09:00", "until 11:00" or "" (all-day).
 * [startMin]/[endMin]: minutes since local midnight, clamped to today (0 for all-day items).
 */
@Serializable
data class AgendaItem(
    val title: String,
    val timeLabel: String,
    val allDay: Boolean,
    val startMin: Int = 0,
    val endMin: Int = 0,
)

@Serializable
data class Agenda(val timed: List<AgendaItem>, val allDay: List<AgendaItem>)

@Serializable
private data class PayloadEvent(
    val title: String = "",
    val start: String = "",
    val end: String = "",
    /** JSON boolean, or text such as "Yes"/"No" as the iPhone Shortcuts app may send it. */
    val allDay: JsonPrimitive? = null,
) {
    val isAllDay: Boolean get() = allDay?.content?.trim()?.lowercase() in setOf("true", "yes", "1", "ja")
}

private const val MINUTES_PER_DAY = 24 * 60
private val json = Json { ignoreUnknownKeys = true }
private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Parses the payload the iPhone Shortcut sends. Returns null for malformed JSON.
 * Events that don't touch [today] in [zone] are dropped; events with unreadable dates are skipped.
 */
fun parseAgenda(text: String, today: LocalDate, zone: ZoneId, t: Texts = Texts.EN): Agenda? {
    val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
    val eventsArray = normaliseEvents(root["events"]) ?: return null
    val dayStart = today.atStartOfDay(zone)
    val dayEnd = today.plusDays(1).atStartOfDay(zone)

    val timed = mutableListOf<Pair<ZonedDateTime, AgendaItem>>()
    val allDay = mutableListOf<AgendaItem>()
    for (elem in eventsArray) {
        val e = runCatching { json.decodeFromJsonElement<PayloadEvent>(elem) }.getOrNull() ?: continue
        val start = parseTime(e.start, zone) ?: continue
        val end = parseTime(e.end, zone) ?: start
        val touchesToday = start < dayEnd && (end > dayStart || start >= dayStart)
        if (!touchesToday) continue
        val title = e.title.trim().ifEmpty { "(no title)" }
        if (e.isAllDay) {
            allDay += AgendaItem(title, "", true)
        } else {
            val label = if (start < dayStart) t.until(end.format(HHMM)) else start.format(HHMM)
            val startMin = if (start < dayStart) 0 else start.hour * 60 + start.minute
            val endMin = if (end >= dayEnd) MINUTES_PER_DAY else end.hour * 60 + end.minute
            timed += start to AgendaItem(title, label, false, startMin, endMin)
        }
    }
    return Agenda(timed.sortedBy { it.first }.map { it.second }, allDay)
}

private fun normaliseEvents(element: JsonElement?): JsonArray? {
    if (element == null) return JsonArray(emptyList())
    if (element is JsonArray) return element
    if (element is JsonObject) return JsonArray(listOf(element))
    if (element is JsonPrimitive && element.isString) {
        val s = element.content.trim()
        if (s.isEmpty() || s == "[]") return JsonArray(emptyList())
        if (s.startsWith("[") && s.endsWith("]")) {
            val parsed = runCatching { json.parseToJsonElement(s) }.getOrNull()
            if (parsed is JsonArray) return parsed
        }
        val objects = splitJsonObjects(s).mapNotNull { objStr ->
            runCatching { json.parseToJsonElement(objStr) }.getOrNull()
        }
        return JsonArray(objects)
    }
    return null
}

private fun splitJsonObjects(s: String): List<String> {
    val results = mutableListOf<String>()
    var depth = 0
    var start = -1
    var inString = false
    var escape = false

    for (i in s.indices) {
        val c = s[i]
        if (inString) {
            if (escape) {
                escape = false
            } else if (c == '\\') {
                escape = true
            } else if (c == '"') {
                inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && start != -1) {
                            results += s.substring(start, i + 1)
                            start = -1
                        }
                    }
                }
            }
        }
    }
    return results
}

private fun parseTime(s: String, zone: ZoneId): ZonedDateTime? =
    runCatching { OffsetDateTime.parse(s).atZoneSameInstant(zone) }.getOrNull()
