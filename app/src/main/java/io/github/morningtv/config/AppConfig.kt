package io.github.morningtv.config

import android.content.SharedPreferences
import java.time.ZoneId

/** How two or more appointments are shown: day timeline (default) or plain list. */
enum class CalendarStyle { TIMELINE, LIST }

/** Language of the screen texts: follow the TV's system language, or force German / English. */
enum class LanguageSetting { AUTO, DE, EN }

/** Screen layout: weather and appointments on one time graph (default) or separate cards. */
enum class ScreenLayout { TIMELINE, CLASSIC }

/** All user settings. Personal values come from the git-ignored local config via [ConfigReceiver]. */
data class AppConfig(
    val tvPsk: String,
    val greetingName: String,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val timezone: ZoneId,
    val standbyMinutes: Int,
    val calendarStyle: CalendarStyle = CalendarStyle.TIMELINE,
    val layout: ScreenLayout = ScreenLayout.TIMELINE,
    /** Secret iCal address of the Google calendar; empty = off. Never logged or shown. */
    val googleIcalUrl: String = "",
    /** Switch for the Google calendar; only has an effect if [googleIcalUrl] is set. */
    val googleEnabled: Boolean = true,
    /** Address (host or host:port) of a Davis WeatherLink Live on the LAN; empty = none. */
    val weatherLinkHost: String = "",
    val weatherLinkEnabled: Boolean = true,
    val language: LanguageSetting = LanguageSetting.AUTO,
) {
    companion object {
        const val DEFAULT_STANDBY_MINUTES = 30

        /** Config keys, identical to `config/example.properties`. */
        val KEYS = listOf(
            "tv_psk", "greeting_name", "latitude", "longitude",
            "location_name", "timezone", "standby_minutes", "calendar_style", "layout", "google_ical_url",
            "google_enabled", "weatherlink_host", "weatherlink_enabled", "language",
        )

        fun fromMap(values: Map<String, String>): AppConfig {
            fun str(key: String) = values[key]?.trim().orEmpty()
            fun coord(key: String) = str(key).toDoubleOrNull() ?: Double.NaN
            val zone = runCatching { ZoneId.of(str("timezone")) }.getOrDefault(ZoneId.systemDefault())
            val minutes = str("standby_minutes").toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_STANDBY_MINUTES
            return AppConfig(
                tvPsk = str("tv_psk"),
                greetingName = str("greeting_name"),
                latitude = coord("latitude"),
                longitude = coord("longitude"),
                locationName = str("location_name"),
                timezone = zone,
                standbyMinutes = minutes,
                googleIcalUrl = str("google_ical_url"),
                googleEnabled = !str("google_enabled").equals("false", ignoreCase = true),
                weatherLinkHost = str("weatherlink_host"),
                weatherLinkEnabled = !str("weatherlink_enabled").equals("false", ignoreCase = true),
                language = when (str("language").lowercase()) {
                    "de" -> LanguageSetting.DE
                    "en" -> LanguageSetting.EN
                    else -> LanguageSetting.AUTO
                },
                layout = if (str("layout").equals("classic", ignoreCase = true)) ScreenLayout.CLASSIC else ScreenLayout.TIMELINE,
                calendarStyle = if (str("calendar_style").equals("list", ignoreCase = true)) CalendarStyle.LIST else CalendarStyle.TIMELINE,
            )
        }
    }
}

/** Persists config values in SharedPreferences. Unknown keys are ignored. */
class ConfigStore(private val prefs: SharedPreferences) {
    fun load(): AppConfig =
        AppConfig.fromMap(AppConfig.KEYS.mapNotNull { k -> prefs.getString(k, null)?.let { k to it } }.toMap())

    /** Returns false if [key] is not a known config key. */
    fun set(key: String, value: String): Boolean {
        if (key !in AppConfig.KEYS) return false
        prefs.edit().putString(key, value).apply()
        return true
    }

    companion object {
        const val PREFS_NAME = "morningtv_config"
    }
}
