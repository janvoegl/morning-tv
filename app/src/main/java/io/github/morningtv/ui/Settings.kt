package io.github.morningtv.ui

import io.github.morningtv.config.AppConfig
import io.github.morningtv.config.CalendarStyle
import io.github.morningtv.config.LanguageSetting
import io.github.morningtv.config.ScreenLayout
import io.github.morningtv.text.SettingsTexts
import io.github.morningtv.weather.Place

enum class SettingId { NAME, PLACE, STANDBY, LANGUAGE, LAYOUT, CALENDAR_STYLE, GOOGLE, WEATHERLINK }

/** CHOICE: Left/Right/OK cycle; TEXT and SEARCH open a dialog; UNAVAILABLE: needs setup via install.sh. */
enum class RowKind { CHOICE, TEXT, SEARCH, UNAVAILABLE }

data class SettingRow(val id: SettingId, val label: String, val value: String, val kind: RowKind)

val STANDBY_CHOICES = listOf(10, 15, 20, 30, 45, 60, 90)

fun settingRows(cfg: AppConfig, st: SettingsTexts): List<SettingRow> {
    fun onOff(on: Boolean) = if (on) st.on else st.off
    return listOf(
        SettingRow(SettingId.NAME, st.name, cfg.greetingName.ifBlank { st.notSet }, RowKind.TEXT),
        SettingRow(SettingId.PLACE, st.place, cfg.locationName.ifBlank { st.notSet }, RowKind.SEARCH),
        SettingRow(SettingId.STANDBY, st.standby, st.minutes(cfg.standbyMinutes), RowKind.CHOICE),
        SettingRow(
            SettingId.LANGUAGE, st.language,
            when (cfg.language) { LanguageSetting.AUTO -> st.auto; LanguageSetting.DE -> st.german; LanguageSetting.EN -> st.english },
            RowKind.CHOICE,
        ),
        SettingRow(SettingId.LAYOUT, st.layout, if (cfg.layout == ScreenLayout.TIMELINE) st.timeGraph else st.classic, RowKind.CHOICE),
        SettingRow(
            SettingId.CALENDAR_STYLE, st.calendarStyle,
            if (cfg.calendarStyle == CalendarStyle.TIMELINE) st.timelineStyle else st.listStyle, RowKind.CHOICE,
        ),
        if (cfg.googleIcalUrl.isEmpty()) SettingRow(SettingId.GOOGLE, st.google, st.notSetUp, RowKind.UNAVAILABLE)
        else SettingRow(SettingId.GOOGLE, st.google, onOff(cfg.googleEnabled), RowKind.CHOICE),
        if (cfg.weatherLinkHost.isEmpty()) SettingRow(SettingId.WEATHERLINK, st.weatherLink, st.notSetUp, RowKind.UNAVAILABLE)
        else SettingRow(SettingId.WEATHERLINK, st.weatherLink, onOff(cfg.weatherLinkEnabled), RowKind.CHOICE),
    )
}

private fun <T> cycle(list: List<T>, current: Int, step: Int): T = list[Math.floorMod(current + step, list.size)]

/**
 * The config change when a CHOICE row is moved by [step] (+1 / -1, wrapping), as key/value for
 * [io.github.morningtv.config.ConfigStore.set]. Null for rows that are not choices.
 */
fun changeChoice(cfg: AppConfig, id: SettingId, step: Int): Pair<String, String>? = when (id) {
    SettingId.STANDBY -> {
        val cur = cfg.standbyMinutes
        val i = STANDBY_CHOICES.indexOf(cur)
        val next = when {
            i >= 0 -> cycle(STANDBY_CHOICES, i, step)
            // A value outside the list (set via install.sh): step to the nearest choice in that direction, wrapping.
            step > 0 -> STANDBY_CHOICES.firstOrNull { it > cur } ?: STANDBY_CHOICES.first()
            else -> STANDBY_CHOICES.lastOrNull { it < cur } ?: STANDBY_CHOICES.last()
        }
        "standby_minutes" to next.toString()
    }
    SettingId.LANGUAGE -> {
        val all = listOf("auto", "de", "en")
        "language" to cycle(all, all.indexOf(cfg.language.name.lowercase()), step)
    }
    SettingId.LAYOUT -> "layout" to if (cfg.layout == ScreenLayout.TIMELINE) "classic" else "timeline"
    SettingId.CALENDAR_STYLE -> "calendar_style" to if (cfg.calendarStyle == CalendarStyle.TIMELINE) "list" else "timeline"
    SettingId.GOOGLE -> if (cfg.googleIcalUrl.isEmpty()) null else "google_enabled" to (!cfg.googleEnabled).toString()
    SettingId.WEATHERLINK -> if (cfg.weatherLinkHost.isEmpty()) null else "weatherlink_enabled" to (!cfg.weatherLinkEnabled).toString()
    SettingId.NAME, SettingId.PLACE -> null
}

/** Config values to store when the user picks [p] in the place search. */
fun placeUpdates(p: Place): List<Pair<String, String>> = buildList {
    add("latitude" to p.latitude.toString())
    add("longitude" to p.longitude.toString())
    add("location_name" to p.name)
    if (p.timezone.isNotBlank()) add("timezone" to p.timezone)
}
