package io.github.morningtv.calendar

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Today's agenda as last received, with the day and the "HH:mm" it arrived at. */
@Serializable
data class CachedAgenda(val date: String, val receivedAt: String, val agenda: Agenda)

private val cacheJson = Json { ignoreUnknownKeys = true }

fun encodeCache(date: LocalDate, receivedAt: String, agenda: Agenda): String =
    cacheJson.encodeToString(CachedAgenda.serializer(), CachedAgenda(date.toString(), receivedAt, agenda))

/** Returns the cached agenda only if it was received on [today]; null if missing, stale or unreadable. */
fun decodeCache(text: String?, today: LocalDate): CachedAgenda? {
    if (text.isNullOrBlank()) return null
    val c = runCatching { cacheJson.decodeFromString(CachedAgenda.serializer(), text) }.getOrNull() ?: return null
    return c.takeIf { it.date == today.toString() }
}
