package io.github.morningtv.text

import io.github.morningtv.config.LanguageSetting
import java.util.Locale

/** Every text the morning screen shows. [forLocale] picks German or English (the fallback). */
data class Texts(
    val locale: Locale,
    val goodMorning: String,
    val goodAfternoon: String,
    val goodEvening: String,
    /** Greeting at night (22:00-04:59). */
    val hello: String,
    val sentWithAlarm: String,
    val noAppointments: String,
    val allDay: String,
    val more: (Int) -> String,
    val until: (String) -> String,
    val rainFrom: (chance: Int, hour: String) -> String,
    val rain: (chance: Int) -> String,
    val noRain: String,
    val highLow: (high: Int, low: Int) -> String,
    val tvOffInMinutes: (Int) -> String,
    val tvOffSoon: String,
    val weatherTitle: String,
    val weatherNotSet: String,
    val weatherUnavailable: String,
    val standbyFailed: String,
    /** DateTimeFormatter pattern for the clock line. */
    val clockPattern: String,
    /** Weather labels, see weatherLabel(). */
    val clear: String,
    val partlyCloudy: String,
    val fog: String,
    val drizzle: String,
    val rainLabel: String,
    val snow: String,
    val showers: String,
    val thunderstorm: String,
    val cloudy: String,
    /** Text after the hour on the timeline: "09 Uhr" / "09:00". */
    val hourSuffix: String,
    /** "As of 06:45". */
    val asOf: (String) -> String,
) {
    companion object {
        val EN = Texts(
            locale = Locale.ENGLISH,
            goodMorning = "Good morning",
            goodAfternoon = "Good afternoon",
            goodEvening = "Good evening",
            hello = "Hello",
            sentWithAlarm = "Appointments arrive with your alarm",
            noAppointments = "No appointments today",
            allDay = "all day",
            more = { "+$it more" },
            until = { "until $it" },
            rainFrom = { c, h -> "Rain $c% from $h" },
            rain = { "Rain $it%" },
            noRain = "No rain expected",
            highLow = { h, l -> "High $h° / Low $l°" },
            tvOffInMinutes = { "TV off in $it min" },
            tvOffSoon = "TV off in <1 min",
            weatherTitle = "TODAY'S WEATHER",
            weatherNotSet = "Weather location not set",
            weatherUnavailable = "Weather unavailable",
            standbyFailed = "Could not switch the TV off",
            clockPattern = "EEE, d MMM  HH:mm",
            clear = "Clear",
            partlyCloudy = "Partly cloudy",
            fog = "Fog",
            drizzle = "Drizzle",
            rainLabel = "Rain",
            snow = "Snow",
            showers = "Showers",
            thunderstorm = "Thunderstorm",
            cloudy = "Cloudy",
            hourSuffix = ":00",
            asOf = { "As of $it" },
        )

        val DE = Texts(
            locale = Locale.GERMAN,
            goodMorning = "Guten Morgen",
            goodAfternoon = "Guten Tag",
            goodEvening = "Guten Abend",
            hello = "Hallo",
            sentWithAlarm = "Termine kommen mit dem Wecker",
            noAppointments = "Heute keine Termine",
            allDay = "ganztägig",
            more = { "+$it weitere" },
            until = { "bis $it" },
            rainFrom = { c, h -> "Regen $c % ab $h" },
            rain = { "Regen $it %" },
            noRain = "Kein Regen erwartet",
            highLow = { h, l -> "Hoch $h° / Tief $l°" },
            tvOffInMinutes = { "TV aus in $it Min." },
            tvOffSoon = "TV aus in <1 Min.",
            weatherTitle = "WETTER HEUTE",
            weatherNotSet = "Wetter-Ort nicht eingestellt",
            weatherUnavailable = "Wetter nicht verfügbar",
            standbyFailed = "TV konnte nicht ausgeschaltet werden",
            clockPattern = "EEE d. MMM  HH:mm",
            clear = "Klar",
            partlyCloudy = "Teils bewölkt",
            fog = "Nebel",
            drizzle = "Nieselregen",
            rainLabel = "Regen",
            snow = "Schnee",
            showers = "Regenschauer",
            thunderstorm = "Gewitter",
            cloudy = "Bewölkt",
            hourSuffix = " Uhr",
            asOf = { "Stand von $it Uhr" },
        )

        fun forLocale(locale: Locale): Texts = if (locale.language == "de") DE else EN

        /** Texts for the configured language; [LanguageSetting.AUTO] follows [system]. */
        fun forSetting(setting: LanguageSetting, system: Locale): Texts = when (setting) {
            LanguageSetting.DE -> DE
            LanguageSetting.EN -> EN
            LanguageSetting.AUTO -> forLocale(system)
        }
    }
}
