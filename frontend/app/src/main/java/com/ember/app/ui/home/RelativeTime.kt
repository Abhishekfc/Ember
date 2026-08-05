package com.ember.app.ui.home

import java.time.Instant
import java.time.temporal.ChronoUnit

/** "2m ago" / "3h ago" / "yesterday" / "3d ago" style formatting, matching the design mockups. */
fun formatRelativeTime(isoInstant: String, now: Instant = Instant.now()): String {
    val then = runCatching { Instant.parse(isoInstant) }.getOrNull() ?: return ""
    val minutes = ChronoUnit.MINUTES.between(then, now)
    val hours = ChronoUnit.HOURS.between(then, now)
    val days = ChronoUnit.DAYS.between(then, now)

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "yesterday"
        else -> "${days}d ago"
    }
}

/** "18h left" / "45m left" style countdown for a photo still in its grace period — see
 * HomeCarouselEntry.isFriendsNewest and buildHomeCarousel for how a photo's [expiresAt] is
 * derived (24 hours after the photo that superseded it). Never negative: a photo whose window
 * has technically already closed but hasn't been re-fetched off the feed yet reads as "expiring"
 * rather than a confusing negative duration. */
fun formatRemainingTime(expiresAt: Instant, now: Instant = Instant.now()): String {
    val minutesLeft = ChronoUnit.MINUTES.between(now, expiresAt)
    if (minutesLeft <= 0) return "expiring"
    val hoursLeft = minutesLeft / 60
    return if (hoursLeft < 1) "${minutesLeft}m left" else "${hoursLeft}h left"
}
