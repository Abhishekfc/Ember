package com.emigo.app.invite

import java.net.URLDecoder

/** Where an invite takes someone: a small page on the Emigo website that says who invited them and
 * sends them to Google Play, and (see [usernameFromReferrer]) hands that name back to the app
 * after the install. */
internal const val INVITE_PAGE_BASE = "https://emigo.live/i/"

/** The store page, used when there is no username to put in a link. */
internal const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.emigo.app"

/** The key the invite page puts in the Play Store link's `referrer`, as `invite=<username>`. */
internal const val INVITE_REFERRER_KEY = "invite"

/** The same rule the server enforces for a username (letters, numbers, `_` and `.`), so a made-up
 * or damaged value is never put in a link or trusted when it comes back. */
private val USERNAME_PATTERN = Regex("^[A-Za-z0-9_.]{1,64}$")

internal fun isValidUsername(value: String?): Boolean = value != null && USERNAME_PATTERN.matches(value)

/** `https://emigo.live/i/ann` for a good username; the plain store page for a missing or odd one,
 * so an invite always has a link that works. */
internal fun inviteLinkFor(username: String?): String {
    val name = username?.trim()
    return if (isValidUsername(name)) INVITE_PAGE_BASE + name else PLAY_STORE_URL
}

/** The username an install came from, read out of Google Play's install referrer
 * (`invite=ann`, possibly with other `&` parts), or null when there isn't a good one. */
internal fun usernameFromReferrer(referrer: String?): String? {
    if (referrer.isNullOrBlank()) return null
    // The referrer is a query string (`a=1&invite=ann`), possibly percent-encoded. Parsed by hand
    // so this stays plain Kotlin that runs in ordinary unit tests.
    for (part in referrer.split('&')) {
        val key = part.substringBefore('=')
        if (key != INVITE_REFERRER_KEY || '=' !in part) continue
        val value = runCatching { URLDecoder.decode(part.substringAfter('='), "UTF-8") }.getOrNull()?.trim()
        return value?.takeIf(::isValidUsername)
    }
    return null
}
