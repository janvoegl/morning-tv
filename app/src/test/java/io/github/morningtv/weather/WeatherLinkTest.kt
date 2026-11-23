package io.github.morningtv.weather

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WeatherLinkTest {

    @Test
    fun parseHostPortValid() {
        assertEquals("192.168.1.5" to 80, parseHostPort("192.168.1.5"))
        assertEquals("host.local" to 8080, parseHostPort("host.local:8080"))
    }

    @Test
    fun parseHostPortRejectsInvalid() {
        val invalidInputs = listOf(
            "",
            "a b",
            "http://x",
            "x/y",
            "x:0",
            "x:70000",
            "x:abc",
            "user@x",
            ":80",
            "host:",
            "host:80:80",
        )
        for (input in invalidInputs) {
            assertNull("Expected null for '$input'", parseHostPort(input))
        }
    }

    @Test
    fun parseOutdoorTempCFixture() {
        val json = """
            {
              "data": {
                "did": "001D0A000001",
                "ts": 1600000000,
                "conditions": [
                  {
                    "data_structure_type": 1,
                    "temp": 50.0,
                    "hum": 58.7
                  },
                  {
                    "data_structure_type": 4,
                    "temp_in": 80.9
                  },
                  {
                    "data_structure_type": 3,
                    "bar_sea_level": 30.1
                  }
                ]
              },
              "error": null
            }
        """.trimIndent()
        assertEquals(10, parseOutdoorTempC(json))
    }

    @Test
    fun parseOutdoorTempCConversions() {
        val json313 = """{"data":{"conditions":[{"data_structure_type":1,"temp":31.3}]},"error":null}"""
        assertEquals(0, parseOutdoorTempC(json313))

        val json14 = """{"data":{"conditions":[{"data_structure_type":1,"temp":14.0}]},"error":null}"""
        assertEquals(-10, parseOutdoorTempC(json14))
    }

    @Test
    fun parseOutdoorTempCIgnoresIndoorAndRejectsInvalid() {
        val indoorOnly = """{"data":{"conditions":[{"data_structure_type":4,"temp_in":80.9}]},"error":null}"""
        assertNull(parseOutdoorTempC(indoorOnly))

        val tempNull = """{"data":{"conditions":[{"data_structure_type":1,"temp":null}]},"error":null}"""
        assertNull(parseOutdoorTempC(tempNull))

        val errorReply = """{"data":null,"error":{"code":1}}"""
        assertNull(parseOutdoorTempC(errorReply))

        val errorWithData = """{"data":{"conditions":[{"data_structure_type":1,"temp":50.0}]},"error":{"code":1}}"""
        assertNull(parseOutdoorTempC(errorWithData))

        assertNull(parseOutdoorTempC("garbage"))
        assertNull(parseOutdoorTempC("{ broken json"))
        assertNull(parseOutdoorTempC(""))

        val absurdTemp = """{"data":{"conditions":[{"data_structure_type":1,"temp":500.0}]},"error":null}"""
        assertNull(parseOutdoorTempC(absurdTemp))
    }

    @Test
    fun rawHttpGetSuccess200() {
        withServer({ client ->
            val out = client.getOutputStream()
            out.write("HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\n\r\nHello Davis!".toByteArray(Charsets.UTF_8))
            out.flush()
        }) { port ->
            val body = rawHttpGet("127.0.0.1", port, "/v1/current_conditions")
            assertEquals("Hello Davis!", body)
        }
    }

    @Test
    fun rawHttpGet404Throws() {
        withServer({ client ->
            val out = client.getOutputStream()
            out.write("HTTP/1.0 404 Not Found\r\n\r\nMissing".toByteArray(Charsets.UTF_8))
            out.flush()
        }) { port ->
            try {
                rawHttpGet("127.0.0.1", port, "/missing")
                fail("Expected IOException on 404")
            } catch (_: IOException) {
                // Expected
            }
        }
    }

    @Test
    fun rawHttpGetMissingHeaderTerminatorThrows() {
        withServer({ client ->
            val out = client.getOutputStream()
            out.write("HTTP/1.0 200 OK\r\nNo-Terminator-Here".toByteArray(Charsets.UTF_8))
            out.flush()
        }) { port ->
            try {
                rawHttpGet("127.0.0.1", port, "/test")
                fail("Expected IOException when header terminator missing")
            } catch (_: IOException) {
                // Expected
            }
        }
    }

    @Test
    fun rawHttpGetOversizedThrows() {
        withServer({ client ->
            val out = client.getOutputStream()
            out.write("HTTP/1.0 200 OK\r\n\r\n".toByteArray(Charsets.UTF_8))
            val chunk = ByteArray(8192)
            repeat(9) { // 9 * 8192 = 73728 > 65536 bytes
                out.write(chunk)
                out.flush()
            }
        }) { port ->
            try {
                rawHttpGet("127.0.0.1", port, "/large")
                fail("Expected IOException when response exceeds 64 KiB")
            } catch (_: IOException) {
                // Expected
            }
        }
    }

    @Test
    fun clientReturnsTemperatureAndPassesPath() {
        var requestedHost: String? = null
        var requestedPort: Int? = null
        var requestedPath: String? = null

        val client = WeatherLinkClient("192.168.1.10:8080", get = { host, port, path ->
            requestedHost = host
            requestedPort = port
            requestedPath = path
            """{"data":{"conditions":[{"data_structure_type":1,"temp":50.0}]},"error":null}"""
        })

        assertEquals(10, client.outdoorTempC())
        assertEquals("192.168.1.10", requestedHost)
        assertEquals(8080, requestedPort)
        assertEquals("/v1/current_conditions", requestedPath)
    }

    @Test
    fun clientReturnsNullWhenLambdaThrows() {
        val client = WeatherLinkClient("192.168.1.10", get = { _, _, _ ->
            throw IOException("network failure")
        })
        assertNull(client.outdoorTempC())
    }

    @Test
    fun clientReturnsNullForInvalidHostWithoutCallingLambda() {
        var called = false
        val client = WeatherLinkClient("invalid host name", get = { _, _, _ ->
            called = true
            """{"data":{"conditions":[{"data_structure_type":1,"temp":50.0}]},"error":null}"""
        })
        assertNull(client.outdoorTempC())
        assertFalse(called)
    }

    private fun withServer(handler: (Socket) -> Unit, block: (Int) -> Unit) {
        val loopback = InetAddress.getByName("127.0.0.1")
        ServerSocket(0, 1, loopback).use { server ->
            val thread = Thread {
                try {
                    server.accept().use(handler)
                } catch (_: Exception) {
                }
            }
            thread.start()
            try {
                block(server.localPort)
            } finally {
                thread.join(2000)
            }
        }
    }
}
