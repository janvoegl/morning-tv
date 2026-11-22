package io.github.morningtv.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchDataTest {
    @Test
    fun postBodyBytesBecomeUtf8String() {
        val json = """{"events":[{"title":"Probe ä"}]}"""
        assertEquals(json, extractPayload(json.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun missingPostBodyIsNotAShortcutLaunch() {
        assertNull(extractPayload(null))
    }

    @Test
    fun emptyPostBodyIsNotAShortcutLaunch() {
        assertNull(extractPayload(ByteArray(0)))
        assertNull(extractPayload("   ".toByteArray()))
    }
}
