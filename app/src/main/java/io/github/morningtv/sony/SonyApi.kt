package io.github.morningtv.sony

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Calls the TV's own Sony REST API (on 127.0.0.1) to switch it to standby. */
class SonyApi(
    private val post: (url: String, headers: Map<String, String>, body: String) -> Pair<Int, String> = ::httpPost,
) {
    fun standby(psk: String): Result<Unit> = runCatching {
        val (status, reply) = post(
            "http://127.0.0.1/sony/system",
            mapOf("X-Auth-PSK" to psk, "Content-Type" to "application/json"),
            """{"method":"setPowerStatus","id":1,"params":[{"status":false}],"version":"1.0"}""",
        )
        if (status != 200) throw IOException("HTTP $status")
        if (reply.contains("\"error\"")) throw IOException("TV refused standby")
    }

    /** [standby] with up to [attempts] tries, [delayMs] apart, so one hiccup doesn't leave the TV on. */
    fun standbyWithRetry(
        psk: String,
        attempts: Int = 3,
        delayMs: Long = 30_000L,
        sleep: (Long) -> Unit = Thread::sleep,
    ): Result<Unit> {
        var result = standby(psk)
        repeat(attempts - 1) {
            if (result.isSuccess) return result
            sleep(delayMs)
            result = standby(psk)
        }
        return result
    }
}

/** Plain HTTP POST returning (status, body). */
fun httpPost(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 5_000
    conn.readTimeout = 5_000
    conn.requestMethod = "POST"
    conn.doOutput = true
    headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
    try {
        conn.outputStream.use { it.write(body.toByteArray()) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return conn.responseCode to (stream?.bufferedReader()?.use { it.readText() } ?: "")
    } finally {
        conn.disconnect()
    }
}
