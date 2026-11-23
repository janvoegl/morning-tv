package io.github.morningtv.transport

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Name of the byte-array extra in which Sony's `setActiveApp` delivers its `data` string. */
const val POST_BODY_EXTRA = "PostBody"

/**
 * Returns the events JSON sent by the iPhone Shortcut, or null when the app was not
 * started by the Shortcut (no or empty PostBody). See docs/tv-findings.md.
 */
fun extractPayload(postBody: ByteArray?): String? =
    postBody?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }

/**
 * True if the Shortcut marked this start with `"keepOn": true` (or "Yes"/"1" as text): the TV was already on, so the
 * app shows its screen but must not arm the standby timer and never switches the TV off.
 */
fun keepsTvOn(payload: String): Boolean = runCatching {
    val v = kotlinx.serialization.json.Json.parseToJsonElement(payload).jsonObject["keepOn"]?.jsonPrimitive?.content
    v?.trim()?.lowercase() in setOf("true", "yes", "1")
}.getOrDefault(false)
