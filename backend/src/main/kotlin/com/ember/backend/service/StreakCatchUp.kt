package com.ember.backend.service

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** When the nightly streak check runs, in UTC: the cron on [StreakBreakDetectionService.detectBrokenStreaks]
 * (`0 5 0 * * *`), which this must match. */
internal val NIGHTLY_RUN_TIME: LocalTime = LocalTime.of(0, 5)

/** The most recent moment the nightly check was due: today at 00:05 UTC, or yesterday's if that
 * hasn't come yet today. A friendship checked at or after this has already had everything the
 * nightly run would look at, since streaks only break at a UTC midnight. */
internal fun mostRecentNightlyRun(now: Instant): Instant {
    val today = now.atZone(ZoneOffset.UTC).toLocalDate()
    val todaysRun = today.atTime(NIGHTLY_RUN_TIME).toInstant(ZoneOffset.UTC)
    return if (now.isBefore(todaysRun)) todaysRun.minus(1, ChronoUnit.DAYS) else todaysRun
}

/**
 * Does the start-up streak check have anything to do? Every restart used to re-check every
 * friendship one by one, which at thousands of friendships is a lot of database work for nothing:
 * the nightly check has normally already done it. Now it only runs when something was missed:
 *
 * - [notCheckedSince]: friendships whose last check is older than the most recent nightly run
 *   (the server was down at 00:05 UTC, or a run stopped part way), or
 * - [checkedFriendships] is fewer than [acceptedFriendships]: friendships that have never been
 *   checked at all (new ones).
 *
 * Skipping changes nothing for anyone: a streak is worked out from the photo history whenever it
 * is asked, and a break is recorded once with dates taken from that history, not from when the
 * check ran. The check only decides when a break gets *noticed* and notified.
 */
internal fun catchUpIsNeeded(acceptedFriendships: Long, checkedFriendships: Long, notCheckedSince: Long): Boolean =
    notCheckedSince > 0 || checkedFriendships < acceptedFriendships
