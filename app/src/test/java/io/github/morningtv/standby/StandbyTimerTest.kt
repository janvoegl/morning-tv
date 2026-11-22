package io.github.morningtv.standby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandbyTimerTest {
    private var now = 1_000_000L
    private val timer = StandbyTimer { now }

    @Test
    fun notArmedHasNoRemaining() {
        assertNull(timer.remainingMs())
        assertFalse(timer.isDue())
    }

    @Test
    fun armThirtyMinutes() {
        timer.arm(30)
        assertEquals(1_800_000L, timer.remainingMs())
    }

    @Test
    fun rearmReplacesEarlier() {
        timer.arm(30)
        now += 600_000
        timer.arm(30)
        assertEquals(1_800_000L, timer.remainingMs())
    }

    @Test
    fun cancelClears() {
        timer.arm(30)
        timer.cancel()
        assertNull(timer.remainingMs())
        assertFalse(timer.isDue())
    }

    @Test
    fun dueAfterTime() {
        timer.arm(1)
        now += 59_999
        assertFalse(timer.isDue())
        now += 1
        assertTrue(timer.isDue())
        now += 10_000
        assertEquals(0L, timer.remainingMs())
    }

    @Test
    fun labelRoundsUp() {
        assertEquals("TV off in 30 min", countdownLabel(29 * 60_000L + 1_000))
        assertEquals("TV off in 29 min", countdownLabel(29 * 60_000L))
        assertEquals("TV off in <1 min", countdownLabel(59_000))
        assertEquals("TV off in <1 min", countdownLabel(0))
    }
}
