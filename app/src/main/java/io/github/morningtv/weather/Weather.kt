package io.github.morningtv.weather

import io.github.morningtv.text.Texts
import java.net.URLEncoder
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class HourPoint(val hour: Int, val tempC: Int, val code: Int)

data class WeatherSummary(
    val nowTempC: Int,
    val nowCode: Int,
    val highC: Int,
    val lowC: Int,
    /** Highest precipitation probability (%) from the current hour on. */
    val rainChance: Int,
    /** First hour from now with precipitation probability >= 30 %, or null. */
    val rainFromHour: Int?,
    val hours: List<HourPoint>,
)

private val SHOWN_HOURS = listOf(7, 9, 12, 15, 18)
private const val RAIN_THRESHOLD = 30

/** Open-Meteo forecast URL for one day at the given location. */
fun forecastUrl(lat: Double, lon: Double, zone: ZoneId): String =
    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
        "&current=temperature_2m,weather_code" +
        "&hourly=temperature_2m,precipitation_probability,weather_code" +
        "&daily=temperature_2m_max,temperature_2m_min" +
        "&timezone=${URLEncoder.encode(zone.id, "UTF-8")}&forecast_days=1"

/** Parses an Open-Meteo response (see [forecastUrl]). Returns null if anything required is missing. */
fun parseForecast(json: String, nowHour: Int): WeatherSummary? = runCatching {
    val root = Json.parseToJsonElement(json).jsonObject
    val current = root.getValue("current").jsonObject
    val hourly = root.getValue("hourly").jsonObject
    val daily = root.getValue("daily").jsonObject

    val hoursOfDay = hourly.arr("time").map { it.jsonPrimitive.contentOrNull!!.substringAfter('T').take(2).toInt() }
    val temps = hourly.arr("temperature_2m").map { it.jsonPrimitive.double }
    val probs = hourly.arr("precipitation_probability").map { it.jsonPrimitive.contentOrNull?.toIntOrNull() ?: 0 }
    val codes = hourly.arr("weather_code").map { it.jsonPrimitive.int }

    val ahead = hoursOfDay.indices.filter { hoursOfDay[it] >= nowHour }
    WeatherSummary(
        nowTempC = current.getValue("temperature_2m").jsonPrimitive.double.roundToInt(),
        nowCode = current.getValue("weather_code").jsonPrimitive.int,
        highC = daily.arr("temperature_2m_max").first().jsonPrimitive.double.roundToInt(),
        lowC = daily.arr("temperature_2m_min").first().jsonPrimitive.double.roundToInt(),
        rainChance = ahead.maxOfOrNull { probs[it] } ?: 0,
        rainFromHour = ahead.firstOrNull { probs[it] >= RAIN_THRESHOLD }?.let { hoursOfDay[it] },
        hours = SHOWN_HOURS.filter { it >= nowHour }.mapNotNull { h ->
            val i = hoursOfDay.indexOf(h)
            if (i < 0) null else HourPoint(h, temps[i].roundToInt(), codes[i])
        },
    )
}.getOrNull()

private fun JsonObject.arr(key: String): JsonArray = getValue(key).jsonArray

/** Short label for a WMO weather code. */
fun weatherLabel(code: Int, t: Texts = Texts.EN): String = when (code) {
    0 -> t.clear
    1, 2 -> t.partlyCloudy
    45, 48 -> t.fog
    in 51..57 -> t.drizzle
    in 61..67 -> t.rainLabel
    in 71..77, 85, 86 -> t.snow
    in 80..82 -> t.showers
    in 95..99 -> t.thunderstorm
    else -> t.cloudy
}

/** Emoji for a WMO weather code. */
fun weatherIcon(code: Int): String = when (code) {
    0 -> "☀"
    1, 2 -> "⛅"
    45, 48 -> "🌫"
    in 51..67, in 80..82 -> "🌧"
    in 71..77, 85, 86 -> "❄"
    in 95..99 -> "⛈"
    else -> "☁"
}
