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

    @Test
    fun keepOnFlagIsReadFromBooleanOrText() {
        assertEquals(true, keepsTvOn("""{"events":[],"keepOn":true}"""))
        assertEquals(true, keepsTvOn("""{"events":[],"keepOn":"true"}"""))
        assertEquals(true, keepsTvOn("""{"events":[],"keepOn":"Yes"}"""))
        assertEquals(true, keepsTvOn("""{"events":[],"keepOn":"Ja"}"""))
        assertEquals(true, keepsTvOn("""{"events":[],"keepOn":"ja"}"""))
        assertEquals(false, keepsTvOn("""{"events":[],"keepOn":false}"""))
        assertEquals(false, keepsTvOn("""{"events":[],"keepOn":"false"}"""))
        assertEquals(false, keepsTvOn("""{"events":[],"keepOn":"Nein"}"""))
        assertEquals(false, keepsTvOn("""{"events":[],"keepOn":"nein"}"""))
        assertEquals(false, keepsTvOn("""{"events":[],"keepOn":"0"}"""))
        assertEquals(false, keepsTvOn("""{"events":[]}"""))
        assertEquals(false, keepsTvOn("{oops"))
    }
}
