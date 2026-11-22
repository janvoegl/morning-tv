package io.github.morningtv.weather

import io.github.morningtv.config.AppConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Loads the forecast with retries: right after the TV wakes up, the network may not be ready yet. */
class WeatherClient(
    private val fetch: (String) -> String = ::httpGet,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    /** Returns null when the location is not set or all attempts fail. */
    fun load(cfg: AppConfig, nowHour: Int): WeatherSummary? {
        if (cfg.latitude.isNaN() || cfg.longitude.isNaN()) return null
        val url = forecastUrl(cfg.latitude, cfg.longitude, cfg.timezone)
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) sleep(RETRY_DELAY_MS)
            val summary = runCatching { parseForecast(fetch(url), nowHour) }.getOrNull()
            if (summary != null) return summary
        }
        return null
    }

    companion object {
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 5_000L
    }
}

/** Plain HTTP GET, throws on any non-200 reply. */
fun httpGet(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    try {
        if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
