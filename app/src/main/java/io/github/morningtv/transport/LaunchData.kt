package io.github.morningtv.transport

/** Name of the byte-array extra in which Sony's `setActiveApp` delivers its `data` string. */
const val POST_BODY_EXTRA = "PostBody"

/**
 * Returns the events JSON sent by the iPhone Shortcut, or null when the app was not
 * started by the Shortcut (no or empty PostBody). See docs/tv-findings.md.
 */
fun extractPayload(postBody: ByteArray?): String? =
    postBody?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
