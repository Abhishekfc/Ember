package com.ember.backend.service

import com.ember.backend.exception.InvalidFriendRequestException

/** Ceiling matching the `display_name varchar(100)` column and the DTOs' own `@Size(max = 100)`. */
private const val DISPLAY_NAME_MAX_LENGTH = 100

/**
 * Normalizes a user-chosen display name to a single line of ordinary text.
 *
 * A display name is the one piece of free-form text in this app that other people see, and it is
 * interpolated verbatim into strings the recipient reads as if the app were speaking: push
 * notification bodies ("<name> sent you a photo"), Activity feed messages, streak warnings. Only
 * length was validated, so a name could contain newlines and control characters and forge extra
 * lines inside someone else's notification — a name of "Priya\n\nEmigo: your session expired, sign
 * in at …" renders in the notification shade as though the app itself sent the second line.
 *
 * Stripping line breaks and control characters removes that entirely while leaving every real
 * name — including non-Latin scripts, emoji and combining marks — completely untouched. Runs of
 * whitespace collapse to a single space so a name can't be padded out to push other text off
 * screen either.
 */
fun sanitizeDisplayName(raw: String): String {
    val cleaned = raw
        // Whitespace first, and to a *space* rather than to nothing: newlines and tabs sit
        // between words, so deleting them outright would run those words together
        // ("Priya\nSharma" becoming "PriyaSharma") instead of flattening the name to one line.
        .replace(Regex("\\s"), " ")
        // Then the remaining category Cc (control) and Cf (format, e.g. bidi overrides that can
        // visually reverse surrounding text) characters, which are never part of a name and carry
        // no spacing meaning to preserve.
        .filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }
        // Finally collapse, which also absorbs the runs the two steps above just created.
        .replace(Regex(" +"), " ")
        .trim()
        .take(DISPLAY_NAME_MAX_LENGTH)

    if (cleaned.isBlank()) throw InvalidFriendRequestException("Display name cannot be blank")
    return cleaned
}
