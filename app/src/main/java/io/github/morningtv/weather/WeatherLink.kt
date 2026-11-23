package io.github.morningtv.weather

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val MAX_HTTP_RESPONSE_BYTES = 64 * 1024
private val STATUS_200_REGEX = Regex("""^HTTP/1\.[0-9]\s+200(\s.*)?$""")

/** "192.168.1.5" or "station.local:8080" -> host to port (default 80). Only [A-Za-z0-9.-] hosts and ports 1..65535; anything else (paths, spaces, '@', '/', empty) -> null. */
fun parseHostPort(text: String): Pair<String, Int>? {
    if (text.isEmpty()) return null
    val colonIndex = text.indexOf(':')
    val (host, port) = if (colonIndex == -1) {
        text to 80
    } else {
        if (text.indexOf(':', colonIndex + 1) != -1) return null
        val hostPart = text.substring(0, colonIndex)
        val portPart = text.substring(colonIndex + 1)
        if (portPart.isEmpty() || portPart.any { !it.isDigit() }) return null
        val portNum = portPart.toIntOrNull() ?: return null
        hostPart to portNum
    }
    if (host.isEmpty() || host.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '.' && it != '-' }) {
        return null
    }
    if (port !in 1..65535) return null
    return host to port
}

/** Outdoor temperature in whole °C from a /v1/current_conditions reply, or null. */
fun parseOutdoorTempC(json: String): Int? = runCatching {
    val root = Json.parseToJsonElement(json).jsonObject
    val error = root["error"]
    if (error != null && error !is JsonNull) return null

    val data = root["data"]?.jsonObject ?: return null
    val conditions = data["conditions"]?.jsonArray ?: return null

    val type1 = conditions.firstOrNull { elem ->
        (elem as? JsonObject)?.get("data_structure_type")?.jsonPrimitive?.intOrNull == 1
    }?.jsonObject ?: return null

    val tempElem = type1["temp"] ?: return null
    if (tempElem is JsonNull) return null
    val tempF = tempElem.jsonPrimitive.doubleOrNull ?: return null

    val tempC = (tempF - 32.0) / 1.8
    if (tempC.isNaN() || tempC.isInfinite() || tempC < -90.0 || tempC > 60.0) return null
    tempC.roundToInt()
}.getOrNull()

/** Plain HTTP/1.0 GET over a raw java.net.Socket (deliberate: no HttpURLConnection, the app's network security config forbids cleartext to LAN hosts). Returns the body; throws IOException unless the status is 200. */
fun rawHttpGet(host: String, port: Int, path: String, timeoutMs: Int = 3_000): String {
    Socket().use { socket ->
        socket.soTimeout = timeoutMs
        socket.connect(InetSocketAddress(host, port), timeoutMs)

        val request = "GET $path HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(request.toByteArray(Charsets.UTF_8))
        out.flush()

        val input = socket.getInputStream()
        val buffer = ByteArray(8192)
        val outStream = ByteArrayOutputStream()
        var totalRead = 0

        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            totalRead += read
            if (totalRead > MAX_HTTP_RESPONSE_BYTES) {
                throw IOException("Response exceeded 64 KiB")
            }
            outStream.write(buffer, 0, read)
        }

        val response = String(outStream.toByteArray(), Charsets.UTF_8)
        val headerEnd = response.indexOf("\r\n\r\n")
        if (headerEnd == -1) {
            throw IOException("Missing HTTP header terminator")
        }

        val firstLine = response.substring(0, headerEnd).substringBefore("\r\n")
        if (!STATUS_200_REGEX.matches(firstLine)) {
            throw IOException("HTTP request failed: $firstLine")
        }

        return response.substring(headerEnd + 4)
    }
}

class WeatherLinkClient(
    private val hostPort: String,
    private val get: (String, Int, String) -> String = { h, p, path -> rawHttpGet(h, p, path) },
) {
    /** Outdoor °C, or null if the host text is invalid, the station is unreachable or the reply is unusable. Never throws. */
    fun outdoorTempC(): Int? = try {
        val (host, port) = parseHostPort(hostPort) ?: return null
        val body = get(host, port, "/v1/current_conditions")
        parseOutdoorTempC(body)
    } catch (_: Exception) {
        null
    }
}
