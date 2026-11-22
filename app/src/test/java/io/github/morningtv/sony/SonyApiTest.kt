package io.github.morningtv.sony

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyApiTest {
    @Test
    fun standbySendsExactRequest() {
        var url = ""; var headers = emptyMap<String, String>(); var body = ""
        val api = SonyApi { u, h, b -> url = u; headers = h; body = b; 200 to """{"result":[],"id":1}""" }
        assertTrue(api.standby("secret").isSuccess)
        assertEquals("http://127.0.0.1/sony/system", url)
        assertEquals("secret", headers["X-Auth-PSK"])
        assertEquals("""{"method":"setPowerStatus","id":1,"params":[{"status":false}],"version":"1.0"}""", body)
    }

    @Test
    fun standbyErrorReplyIsFailure() {
        val api = SonyApi { _, _, _ -> 200 to """{"error":[403,"Forbidden"],"id":1}""" }
        assertTrue(api.standby("wrong").isFailure)
    }

    @Test
    fun standbyHttpErrorIsFailure() {
        val api = SonyApi { _, _, _ -> 500 to "" }
        assertTrue(api.standby("x").isFailure)
    }

    @Test
    fun standbyExceptionIsFailure() {
        val api = SonyApi { _, _, _ -> throw IOException("refused") }
        assertTrue(api.standby("x").isFailure)
    }
}
