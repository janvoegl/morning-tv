package io.github.morningtv.weather

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One result of a place search. [label] is e.g. "München, Bayern, Deutschland". */
data class Place(val name: String, val label: String, val latitude: Double, val longitude: Double, val timezone: String)

/** Open-Meteo geocoding URL (free, no key). [language] is "de" or "en". */
fun geocodingUrl(query: String, language: String, count: Int = 6): String =
    "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}" +
        "&count=$count&language=$language&format=json"

/** Parses a geocoding reply. Returns an empty list for no hits or unreadable JSON. */
fun parsePlaces(json: String): List<Place> = runCatching {
    val results = Json.parseToJsonElement(json).jsonObject["results"]?.jsonArray ?: return@runCatching emptyList()
    results.mapNotNull { r ->
        val o = r.jsonObject
        val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val label = listOf(name) + listOfNotNull("admin1", "country").mapNotNull { o[it]?.jsonPrimitive?.contentOrNull }
        Place(
            name = name,
            label = label.distinct().joinToString(", "),
            latitude = o["latitude"]?.jsonPrimitive?.double ?: return@mapNotNull null,
            longitude = o["longitude"]?.jsonPrimitive?.double ?: return@mapNotNull null,
            timezone = o["timezone"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
}.getOrDefault(emptyList())
