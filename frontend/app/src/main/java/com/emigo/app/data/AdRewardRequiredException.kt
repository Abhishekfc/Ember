package com.emigo.app.data

import com.emigo.app.data.remote.dto.ErrorResponse

/** Reads the numbers out of the server's 402 body; anything missing or odd counts as one ad, none
 * seen, so a server that doesn't send them still works. */
internal fun adRewardRequired(body: ErrorResponse?): AdRewardRequiredException {
    val required = (body?.adsRequired ?: 1).coerceAtLeast(1)
    val watched = (body?.adsWatched ?: 0).coerceIn(0, required)
    return AdRewardRequiredException(required, watched)
}

/** The server's answer (HTTP 402) when someone without Emigo Gold asks to restore a streak and it
 * doesn't have enough watched ads on record for them. [required] is how many ads a restore takes
 * and [watched] how many the server already has, so the app knows how many more to show. Right
 * after an ad ends it also means "Google hasn't told us yet", so the caller asks again for a few
 * seconds before giving up. A server that doesn't say (an older one) counts as one ad, none seen. */
class AdRewardRequiredException(
    val required: Int = 1,
    val watched: Int = 0,
) : Exception("Watch $required ads or get Emigo Gold to restore this streak")
