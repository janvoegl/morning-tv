package io.github.morningtv.calendar

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** One occurrence touching the day. For all-day events start/end are the date range at 00:00 in [zone] (end exclusive). */
data class IcalOccurrence(
    val title: String,
    val allDay: Boolean,
    val start: ZonedDateTime,
    val end: ZonedDateTime
)

/**
 * Pure Kotlin iCalendar parser that returns all occurrences touching [day] in [zone].
 * Never throws; unreadable events are skipped. Result is sorted by start time.
 */
fun parseIcal(text: String, day: LocalDate, zone: ZoneId): List<IcalOccurrence> {
    return runCatching {
        val lines = unfoldLines(text)
        val vEvents = extractVEvents(lines, zone)
        processEvents(vEvents, day, zone)
    }.getOrDefault(emptyList())
}

private fun unfoldLines(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val unfolded = text.replace(Regex("(\r\n|\r|\n)[ \t]"), "")
    return unfolded.split(Regex("\r\n|\r|\n"))
}

private fun unescapeText(text: String): String {
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length) {
            when (val next = text[i + 1]) {
                'n', 'N' -> { sb.append('\n'); i += 2 }
                ',', ';', '\\' -> { sb.append(next); i += 2 }
                else -> { sb.append(c); i++ }
            }
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private data class RawProp(
    val name: String,
    val params: Map<String, String>,
    val value: String
)

private fun parseProperty(line: String): RawProp? {
    var inQuotes = false
    var colonIdx = -1
    for (i in line.indices) {
        val c = line[i]
        if (c == '"') inQuotes = !inQuotes
        else if (c == ':' && !inQuotes) {
            colonIdx = i
            break
        }
    }
    if (colonIdx < 0) return null
    val header = line.substring(0, colonIdx)
    val value = line.substring(colonIdx + 1)

    val parts = header.split(';')
    val name = parts[0].trim().uppercase()
    if (name.isEmpty()) return null

    val params = mutableMapOf<String, String>()
    for (i in 1 until parts.size) {
        val p = parts[i]
        val eq = p.indexOf('=')
        if (eq > 0) {
            val pName = p.substring(0, eq).trim().uppercase()
            val pVal = p.substring(eq + 1).trim().removeSurrounding("\"")
            params[pName] = pVal
        }
    }
    return RawProp(name, params, value)
}

private data class CalDateTime(
    val isDate: Boolean,
    val date: LocalDate,
    val time: LocalTime,
    val zone: ZoneId
) {
    fun toZonedDateTime(): ZonedDateTime =
        if (isDate) date.atStartOfDay(zone)
        else ZonedDateTime.of(date, time, zone)

    fun toInstant(): Instant = toZonedDateTime().toInstant()
}

private fun parseCalDateTime(value: String, params: Map<String, String>, defaultZone: ZoneId): CalDateTime? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null

    val isDateParam = params["VALUE"]?.equals("DATE", ignoreCase = true) == true
    val clean = trimmed.replace("-", "").replace(":", "")

    if (isDateParam || (clean.length == 8 && clean.all { it.isDigit() })) {
        val date = runCatching {
            LocalDate.of(
                clean.substring(0, 4).toInt(),
                clean.substring(4, 6).toInt(),
                clean.substring(6, 8).toInt()
            )
        }.getOrNull() ?: return null
        return CalDateTime(isDate = true, date = date, time = LocalTime.MIDNIGHT, zone = defaultZone)
    }

    val isUtc = clean.endsWith("Z", ignoreCase = true)
    val dtPart = if (isUtc) clean.dropLast(1) else clean
    val tIdx = dtPart.indexOf('T')
    if (tIdx < 0) return null

    val datePart = dtPart.substring(0, tIdx)
    val timePart = dtPart.substring(tIdx + 1)
    if (datePart.length < 8) return null

    val date = runCatching {
        LocalDate.of(
            datePart.substring(0, 4).toInt(),
            datePart.substring(4, 6).toInt(),
            datePart.substring(6, 8).toInt()
        )
    }.getOrNull() ?: return null

    val time = runCatching {
        val hour = timePart.substring(0, 2).toInt()
        val min = timePart.substring(2, 4).toInt()
        val sec = if (timePart.length >= 6) timePart.substring(4, 6).toInt() else 0
        LocalTime.of(hour, min, sec)
    }.getOrNull() ?: return null

    val eventZone = if (isUtc) {
        ZoneOffset.UTC
    } else {
        val tzid = params["TZID"]
        if (tzid != null) {
            runCatching { ZoneId.of(tzid.removeSurrounding("\"")) }.getOrElse { defaultZone }
        } else {
            defaultZone
        }
    }

    return CalDateTime(isDate = false, date = date, time = time, zone = eventZone)
}

private data class ParsedVEvent(
    val uid: String,
    val summary: String?,
    val status: String?,
    val dtstart: CalDateTime?,
    val dtend: CalDateTime?,
    val recurrenceId: CalDateTime?,
    val rrule: String?,
    val exdates: List<CalDateTime>
)

private fun extractVEvents(lines: List<String>, zone: ZoneId): List<ParsedVEvent> {
    val events = mutableListOf<ParsedVEvent>()
    var inVEvent = false
    var subDepth = 0

    var uid = ""
    var summary: String? = null
    var status: String? = null
    var dtstart: CalDateTime? = null
    var dtend: CalDateTime? = null
    var recurrenceId: CalDateTime? = null
    var rrule: String? = null
    val exdates = mutableListOf<CalDateTime>()

    fun reset() {
        uid = ""
        summary = null
        status = null
        dtstart = null
        dtend = null
        recurrenceId = null
        rrule = null
        exdates.clear()
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.equals("BEGIN:VEVENT", ignoreCase = true)) {
            inVEvent = true
            subDepth = 0
            reset()
            continue
        }
        if (!inVEvent) continue

        if (trimmed.equals("END:VEVENT", ignoreCase = true)) {
            inVEvent = false
            events.add(
                ParsedVEvent(
                    uid = uid,
                    summary = summary,
                    status = status,
                    dtstart = dtstart,
                    dtend = dtend,
                    recurrenceId = recurrenceId,
                    rrule = rrule,
                    exdates = exdates.toList()
                )
            )
            reset()
            continue
        }

        if (trimmed.startsWith("BEGIN:", ignoreCase = true)) {
            subDepth++
            continue
        }
        if (trimmed.startsWith("END:", ignoreCase = true)) {
            if (subDepth > 0) subDepth--
            continue
        }
        if (subDepth > 0) continue

        val prop = parseProperty(trimmed) ?: continue
        when (prop.name) {
            "UID" -> uid = prop.value.trim()
            "SUMMARY" -> summary = unescapeText(prop.value)
            "STATUS" -> status = prop.value.trim().uppercase()
            "DTSTART" -> dtstart = parseCalDateTime(prop.value, prop.params, zone)
            "DTEND" -> dtend = parseCalDateTime(prop.value, prop.params, zone)
            "RECURRENCE-ID" -> recurrenceId = parseCalDateTime(prop.value, prop.params, zone)
            "RRULE" -> rrule = prop.value.trim()
            "EXDATE" -> {
                val parts = prop.value.split(',')
                for (p in parts) {
                    val dt = parseCalDateTime(p, prop.params, zone)
                    if (dt != null) exdates.add(dt)
                }
            }
        }
    }
    return events
}

private sealed interface ByDaySpec {
    data class Plain(val dayOfWeek: DayOfWeek) : ByDaySpec
    data class Nth(val nth: Int, val dayOfWeek: DayOfWeek) : ByDaySpec
}

private data class ParsedRRule(
    val freq: String,
    val interval: Int,
    val count: Int?,
    val until: CalDateTime?,
    val byDay: List<ByDaySpec>?,
    val byMonthDay: List<Int>?,
    val byMonth: List<Int>?,
    val wkst: DayOfWeek
)

private fun parseDayOfWeek(s: String): DayOfWeek? = when (s.uppercase()) {
    "MO" -> DayOfWeek.MONDAY
    "TU" -> DayOfWeek.TUESDAY
    "WE" -> DayOfWeek.WEDNESDAY
    "TH" -> DayOfWeek.THURSDAY
    "FR" -> DayOfWeek.FRIDAY
    "SA" -> DayOfWeek.SATURDAY
    "SU" -> DayOfWeek.SUNDAY
    else -> null
}

private val nthDayRegex = Regex("^([+-]?[1-5])(MO|TU|WE|TH|FR|SA|SU)$", RegexOption.IGNORE_CASE)

private fun parseRRule(rruleStr: String, defaultZone: ZoneId): ParsedRRule? {
    val parts = rruleStr.split(';')
    var freq: String? = null
    var interval = 1
    var count: Int? = null
    var until: CalDateTime? = null
    var byDay: List<ByDaySpec>? = null
    var byMonthDay: List<Int>? = null
    var byMonth: List<Int>? = null
    var wkst = DayOfWeek.MONDAY

    val supportedKeys = setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "WKST")

    for (part in parts) {
        val kv = part.split('=', limit = 2)
        if (kv.size != 2) return null
        val key = kv[0].trim().uppercase()
        val v = kv[1].trim()
        if (key !in supportedKeys) return null

        when (key) {
            "FREQ" -> {
                val f = v.uppercase()
                if (f !in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) return null
                freq = f
            }
            "INTERVAL" -> {
                val iv = v.toIntOrNull() ?: return null
                if (iv < 1) return null
                interval = iv
            }
            "COUNT" -> {
                val c = v.toIntOrNull() ?: return null
                if (c < 1) return null
                count = c
            }
            "UNTIL" -> {
                until = parseCalDateTime(v, emptyMap(), defaultZone) ?: return null
            }
            "BYDAY" -> {
                val days = mutableListOf<ByDaySpec>()
                for (item in v.split(',')) {
                    val plain = parseDayOfWeek(item)
                    if (plain != null) {
                        days.add(ByDaySpec.Plain(plain))
                    } else {
                        val match = nthDayRegex.matchEntire(item) ?: return null
                        val nth = match.groupValues[1].toInt()
                        val dow = parseDayOfWeek(match.groupValues[2]) ?: return null
                        days.add(ByDaySpec.Nth(nth, dow))
                    }
                }
                byDay = days
            }
            "BYMONTHDAY" -> {
                val mdays = mutableListOf<Int>()
                for (item in v.split(',')) {
                    val d = item.toIntOrNull() ?: return null
                    if (d !in 1..31 && d !in -31..-1) return null
                    mdays.add(d)
                }
                byMonthDay = mdays
            }
            "BYMONTH" -> {
                val months = mutableListOf<Int>()
                for (item in v.split(',')) {
                    val m = item.toIntOrNull() ?: return null
                    if (m !in 1..12) return null
                    months.add(m)
                }
                byMonth = months
            }
            "WKST" -> {
                wkst = parseDayOfWeek(v) ?: return null
            }
        }
    }

    if (freq == null) return null
    if ((freq == "DAILY" || freq == "WEEKLY") && byDay?.any { it is ByDaySpec.Nth } == true) {
        return null
    }

    return ParsedRRule(
        freq = freq,
        interval = interval,
        count = count,
        until = until,
        byDay = byDay,
        byMonthDay = byMonthDay,
        byMonth = byMonth,
        wkst = wkst
    )
}

private fun touchesDay(
    allDay: Boolean,
    start: ZonedDateTime,
    end: ZonedDateTime,
    day: LocalDate,
    zone: ZoneId
): Boolean {
    val dayStart = day.atStartOfDay(zone)
    val dayEnd = day.plusDays(1).atStartOfDay(zone)
    return if (allDay) {
        val startDate = start.toLocalDate()
        val endDate = end.toLocalDate()
        day >= startDate && day < endDate
    } else {
        if (start == end) {
            start >= dayStart && start < dayEnd
        } else {
            end > dayStart && start < dayEnd
        }
    }
}

private fun findNthWeekday(ym: YearMonth, nth: Int, dow: DayOfWeek): LocalDate? {
    return if (nth > 0) {
        val first = ym.atDay(1).with(TemporalAdjusters.firstInMonth(dow))
        val target = first.plusWeeks((nth - 1).toLong())
        if (target.month == ym.month) target else null
    } else if (nth < 0) {
        val last = ym.atEndOfMonth().with(TemporalAdjusters.lastInMonth(dow))
        val target = last.minusWeeks((-nth - 1).toLong())
        if (target.month == ym.month) target else null
    } else {
        null
    }
}

private fun getMonthlyCandidates(ym: YearMonth, dtstartDate: LocalDate, rrule: ParsedRRule): List<LocalDate> {
    val dates = mutableListOf<LocalDate>()
    val byDay = rrule.byDay
    val byMonthDay = rrule.byMonthDay
    val length = ym.lengthOfMonth()

    if (byDay != null) {
        for (spec in byDay) {
            when (spec) {
                is ByDaySpec.Plain -> {
                    var d = ym.atDay(1).with(TemporalAdjusters.firstInMonth(spec.dayOfWeek))
                    while (d.month == ym.month) {
                        dates.add(d)
                        d = d.plusWeeks(1)
                    }
                }
                is ByDaySpec.Nth -> {
                    val d = findNthWeekday(ym, spec.nth, spec.dayOfWeek)
                    if (d != null) dates.add(d)
                }
            }
        }
    } else if (byMonthDay != null) {
        for (dayVal in byMonthDay) {
            val d = if (dayVal > 0) dayVal else length + dayVal + 1
            if (d in 1..length) {
                dates.add(ym.atDay(d))
            }
        }
    } else {
        val dom = dtstartDate.dayOfMonth
        if (dom in 1..length) {
            dates.add(ym.atDay(dom))
        }
    }

    if (byDay != null && byMonthDay != null) {
        val validDays = byMonthDay.map { if (it > 0) it else length + it + 1 }.toSet()
        return dates.filter { it.dayOfMonth in validDays }.distinct().sorted()
    }
    return dates.distinct().sorted()
}

private fun createOccurrence(
    title: String,
    dtstart: CalDateTime,
    dtend: CalDateTime?,
    zone: ZoneId
): IcalOccurrence {
    return if (dtstart.isDate) {
        val startDate = dtstart.date
        val endDate = dtend?.date ?: startDate.plusDays(1)
        IcalOccurrence(
            title = title,
            allDay = true,
            start = startDate.atStartOfDay(zone),
            end = endDate.atStartOfDay(zone)
        )
    } else {
        val startZdt = dtstart.toZonedDateTime().withZoneSameInstant(zone)
        val endZdt = (dtend?.toZonedDateTime() ?: dtstart.toZonedDateTime()).withZoneSameInstant(zone)
        IcalOccurrence(
            title = title,
            allDay = false,
            start = startZdt,
            end = endZdt
        )
    }
}

private fun processEvents(events: List<ParsedVEvent>, day: LocalDate, zone: ZoneId): List<IcalOccurrence> {
    val results = mutableListOf<IcalOccurrence>()
    val seen = mutableSetOf<Pair<String, ZonedDateTime>>()

    val overridesByUid = mutableMapOf<String, MutableList<ParsedVEvent>>()
    val baseEventsByUid = mutableMapOf<String, ParsedVEvent>()
    val standaloneEvents = mutableListOf<ParsedVEvent>()

    for (e in events) {
        val effectiveUid = e.uid.ifEmpty { UUID.randomUUID().toString() }
        val ev = e.copy(uid = effectiveUid)
        if (ev.recurrenceId != null) {
            overridesByUid.getOrPut(ev.uid) { mutableListOf() }.add(ev)
        } else if (ev.rrule != null) {
            baseEventsByUid[ev.uid] = ev
        } else {
            standaloneEvents.add(ev)
        }
    }

    // Process standalone non-recurring events
    for (event in standaloneEvents) {
        if (event.status?.equals("CANCELLED", ignoreCase = true) == true) continue
        val dtstart = event.dtstart ?: continue
        val title = event.summary?.takeIf { it.isNotEmpty() } ?: "(no title)"
        val occ = createOccurrence(title, dtstart, event.dtend, zone)
        if (touchesDay(occ.allDay, occ.start, occ.end, day, zone)) {
            if (seen.add(event.uid to occ.start)) {
                results.add(occ)
            }
        }
    }

    val handledOverrides = mutableSetOf<ParsedVEvent>()

    // Process recurring base events
    for ((uid, event) in baseEventsByUid) {
        if (event.status?.equals("CANCELLED", ignoreCase = true) == true) continue
        val dtstart = event.dtstart ?: continue
        val title = event.summary?.takeIf { it.isNotEmpty() } ?: "(no title)"
        val overrides = overridesByUid[uid] ?: emptyList()

        val rrule = event.rrule?.let { parseRRule(it, zone) }
        if (rrule == null) {
            // Unsupported rule part: treat as non-recurring (just its DTSTART instance)
            val occ = createOccurrence(title, dtstart, event.dtend, zone)
            if (touchesDay(occ.allDay, occ.start, occ.end, day, zone)) {
                if (seen.add(event.uid to occ.start)) {
                    results.add(occ)
                }
            }
            continue
        }

        val exdateDates = event.exdates.filter { it.isDate }.map { it.date }.toSet()
        val exdateInstants = event.exdates.filter { !it.isDate }.map { it.toInstant() }.toSet()

        val isAllDay = dtstart.isDate
        val dtstartDate = dtstart.date
        val dtstartTime = dtstart.time
        val eventZone = dtstart.zone
        val durationDays = if (isAllDay) {
            val endD = event.dtend?.date ?: dtstartDate.plusDays(1)
            ChronoUnit.DAYS.between(dtstartDate, endD).coerceAtLeast(1L)
        } else 0L

        val duration = if (!isAllDay) {
            val sZdt = dtstart.toZonedDateTime()
            val eZdt = event.dtend?.toZonedDateTime() ?: sZdt
            val d = Duration.between(sZdt, eZdt)
            if (d.isNegative) Duration.ZERO else d
        } else Duration.ZERO

        fun isUntilPassed(candidateDate: LocalDate): Boolean {
            val until = rrule.until ?: return false
            return if (until.isDate) {
                candidateDate > until.date
            } else {
                if (isAllDay) {
                    candidateDate.atStartOfDay(zone).toInstant() > until.toInstant()
                } else {
                    val inst = LocalDateTime.of(candidateDate, dtstartTime).atZone(eventZone).toInstant()
                    inst > until.toInstant()
                }
            }
        }

        fun checkInstance(candidateDate: LocalDate) {
            val instInstant = if (!isAllDay) {
                LocalDateTime.of(candidateDate, dtstartTime).atZone(eventZone).toInstant()
            } else null

            // EXDATE check
            if (exdateDates.contains(candidateDate)) return
            if (instInstant != null && exdateInstants.contains(instInstant)) return

            // RECURRENCE-ID override check
            val matchedOverride = overrides.firstOrNull { ov ->
                val rec = ov.recurrenceId ?: return@firstOrNull false
                if (isAllDay) {
                    rec.date == candidateDate
                } else {
                    if (rec.isDate) rec.date == candidateDate
                    else rec.toInstant() == instInstant
                }
            }

            if (matchedOverride != null) {
                handledOverrides.add(matchedOverride)
                if (matchedOverride.status?.equals("CANCELLED", ignoreCase = true) != true) {
                    val ovStart = matchedOverride.dtstart
                    if (ovStart != null) {
                        val ovTitle = matchedOverride.summary?.takeIf { it.isNotEmpty() } ?: title
                        val occ = createOccurrence(ovTitle, ovStart, matchedOverride.dtend, zone)
                        if (touchesDay(occ.allDay, occ.start, occ.end, day, zone)) {
                            if (seen.add(uid to occ.start)) {
                                results.add(occ)
                            }
                        }
                    }
                }
                return
            }

            // Normal generated instance
            if (isAllDay) {
                val startZdt = candidateDate.atStartOfDay(zone)
                val endZdt = candidateDate.plusDays(durationDays).atStartOfDay(zone)
                if (touchesDay(true, startZdt, endZdt, day, zone)) {
                    val occ = IcalOccurrence(title, allDay = true, start = startZdt, end = endZdt)
                    if (seen.add(uid to occ.start)) {
                        results.add(occ)
                    }
                }
            } else {
                val startEventZone = LocalDateTime.of(candidateDate, dtstartTime).atZone(eventZone)
                val startZdt = startEventZone.withZoneSameInstant(zone)
                val endZdt = startZdt.plus(duration)
                if (touchesDay(false, startZdt, endZdt, day, zone)) {
                    val occ = IcalOccurrence(title, allDay = false, start = startZdt, end = endZdt)
                    if (seen.add(uid to occ.start)) {
                        results.add(occ)
                    }
                }
            }
        }

        var iterations = 0
        var generatedCount = 0

        when (rrule.freq) {
            "DAILY" -> {
                var curr = dtstartDate
                if (rrule.count == null) {
                    val daysBefore = ChronoUnit.DAYS.between(curr, day.minusDays(7))
                    if (daysBefore > 0) {
                        val steps = daysBefore / rrule.interval
                        curr = curr.plusDays(steps * rrule.interval)
                    }
                }

                while (iterations++ < 5000) {
                    if (isUntilPassed(curr)) break
                    if (rrule.count != null && generatedCount >= rrule.count) break

                    val matchMonth = rrule.byMonth == null || curr.monthValue in rrule.byMonth
                    val matchMonthDay = if (rrule.byMonthDay == null) true else {
                        val dom = curr.dayOfMonth
                        val negDom = dom - curr.lengthOfMonth() - 1
                        dom in rrule.byMonthDay || negDom in rrule.byMonthDay
                    }
                    val matchDay = if (rrule.byDay == null) true else {
                        rrule.byDay.any { (it as ByDaySpec.Plain).dayOfWeek == curr.dayOfWeek }
                    }

                    if (matchMonth && matchMonthDay && matchDay) {
                        generatedCount++
                        checkInstance(curr)
                    }

                    if (curr > day.plusDays(2)) break
                    curr = curr.plusDays(rrule.interval.toLong())
                }
            }
            "WEEKLY" -> {
                val wkst = rrule.wkst
                val dtstartWeekStart = dtstartDate.with(TemporalAdjusters.previousOrSame(wkst))
                var currentWeekStart = dtstartWeekStart
                if (rrule.count == null) {
                    val targetWeek = day.minusDays(14).with(TemporalAdjusters.previousOrSame(wkst))
                    val weeksBefore = ChronoUnit.WEEKS.between(currentWeekStart, targetWeek)
                    if (weeksBefore > 0) {
                        val steps = weeksBefore / rrule.interval
                        currentWeekStart = currentWeekStart.plusWeeks(steps * rrule.interval)
                    }
                }

                val byDayList = rrule.byDay?.map { (it as ByDaySpec.Plain).dayOfWeek } ?: listOf(dtstartDate.dayOfWeek)
                var stop = false

                while (!stop && iterations++ < 5000) {
                    for (offset in 0..6) {
                        val candidate = currentWeekStart.plusDays(offset.toLong())
                        if (candidate.dayOfWeek in byDayList) {
                            if (candidate < dtstartDate) continue
                            if (isUntilPassed(candidate)) {
                                stop = true
                                break
                            }
                            if (rrule.count != null && generatedCount >= rrule.count) {
                                stop = true
                                break
                            }
                            generatedCount++
                            checkInstance(candidate)
                        }
                    }
                    if (currentWeekStart > day.plusDays(7)) break
                    currentWeekStart = currentWeekStart.plusWeeks(rrule.interval.toLong())
                }
            }
            "MONTHLY" -> {
                val startYm = YearMonth.from(dtstartDate)
                var currentYm = startYm
                if (rrule.count == null) {
                    val monthsBefore = ChronoUnit.MONTHS.between(currentYm, YearMonth.from(day).minusMonths(2))
                    if (monthsBefore > 0) {
                        val steps = monthsBefore / rrule.interval
                        currentYm = currentYm.plusMonths(steps * rrule.interval)
                    }
                }

                var stop = false
                while (!stop && iterations++ < 5000) {
                    val inMonth = rrule.byMonth == null || currentYm.monthValue in rrule.byMonth
                    if (inMonth) {
                        val candidates = getMonthlyCandidates(currentYm, dtstartDate, rrule)
                        for (candidate in candidates) {
                            if (candidate < dtstartDate) continue
                            if (isUntilPassed(candidate)) {
                                stop = true
                                break
                            }
                            if (rrule.count != null && generatedCount >= rrule.count) {
                                stop = true
                                break
                            }
                            generatedCount++
                            checkInstance(candidate)
                        }
                    }
                    if (currentYm > YearMonth.from(day).plusMonths(1)) break
                    currentYm = currentYm.plusMonths(rrule.interval.toLong())
                }
            }
            "YEARLY" -> {
                var currentYear = dtstartDate.year
                if (rrule.count == null) {
                    val yearsBefore = day.year - 1 - currentYear
                    if (yearsBefore > 0) {
                        val steps = yearsBefore / rrule.interval
                        currentYear += (steps * rrule.interval).toInt()
                    }
                }

                var stop = false
                while (!stop && iterations++ < 5000) {
                    val months = rrule.byMonth ?: listOf(dtstartDate.monthValue)
                    for (m in months) {
                        val ym = YearMonth.of(currentYear, m)
                        val candidates = getMonthlyCandidates(ym, dtstartDate, rrule)
                        for (candidate in candidates) {
                            if (candidate < dtstartDate) continue
                            if (isUntilPassed(candidate)) {
                                stop = true
                                break
                            }
                            if (rrule.count != null && generatedCount >= rrule.count) {
                                stop = true
                                break
                            }
                            generatedCount++
                            checkInstance(candidate)
                        }
                    }
                    if (currentYear > day.year + 1) break
                    currentYear += rrule.interval
                }
            }
        }
    }

    // Process any unhandled overrides (e.g. moved to today or standalone overrides)
    for ((uid, overrides) in overridesByUid) {
        val baseEvent = baseEventsByUid[uid]
        val baseTitle = baseEvent?.summary?.takeIf { it.isNotEmpty() } ?: "(no title)"
        for (ov in overrides) {
            if (ov in handledOverrides) continue
            if (ov.status?.equals("CANCELLED", ignoreCase = true) == true) continue
            val dtstart = ov.dtstart ?: continue
            val title = ov.summary?.takeIf { it.isNotEmpty() } ?: baseTitle
            val occ = createOccurrence(title, dtstart, ov.dtend, zone)
            if (touchesDay(occ.allDay, occ.start, occ.end, day, zone)) {
                if (seen.add(uid to occ.start)) {
                    results.add(occ)
                }
            }
        }
    }

    results.sortBy { it.start }
    return results
}
