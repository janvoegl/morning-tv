package io.github.morningtv.config

import android.content.SharedPreferences
import java.time.ZoneId

/** All user settings. Personal values come from the git-ignored local config via [ConfigReceiver]. */
data class AppConfig(
    val tvPsk: String,
    val greetingName: String,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val timezone: ZoneId,
    val standbyMinutes: Int,
) {
    companion object {
        const val DEFAULT_STANDBY_MINUTES = 30

        /** Config keys, identical to `config/example.properties`. */
        val KEYS = listOf(
            "tv_psk", "greeting_name", "latitude", "longitude",
            "location_name", "timezone", "standby_minutes",
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
