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
