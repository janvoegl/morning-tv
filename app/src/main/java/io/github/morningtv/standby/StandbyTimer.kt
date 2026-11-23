package io.github.morningtv.standby

import io.github.morningtv.text.Texts

/** Tracks when the TV should go to standby. Pure logic; [now] returns epoch millis. */
class StandbyTimer(private val now: () -> Long) {
    private var dueAt: Long? = null

    /** Arms (or re-arms) the timer; replaces any earlier arming. */
    fun arm(minutes: Int) {
        dueAt = now() + minutes * 60_000L
    }

    fun cancel() {
        dueAt = null
    }

    /** Millis until standby, never negative; null when not armed. */
    fun remainingMs(): Long? = dueAt?.let { maxOf(0L, it - now()) }

    fun isDue(): Boolean = remainingMs() == 0L
}

/** "TV off in 29 min" (minutes rounded up), or "TV off in <1 min". */
fun countdownLabel(remainingMs: Long, t: Texts = Texts.EN): String {
    if (remainingMs < 60_000L) return t.tvOffSoon
    val minutes = (remainingMs + 59_999L) / 60_000L
    return t.tvOffInMinutes(minutes.toInt())
}
