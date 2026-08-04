package com.ember.backend.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** One photo exchange between the current user and a specific friend, tagged with which
 * direction it went — a streak day now requires activity in both directions (see
 * [StreakCalculator.compute]), so the direction has to travel with the timestamp. */
data class StreakExchange(val timestamp: Instant, val sentByMe: Boolean)

object StreakCalculator {

    /**
     * Consecutive-day streak, mutual/Locket-style: a day only counts if BOTH people sent at
     * least one photo that day. One person sending without the other ever responding no longer
     * extends or maintains the streak — matches how Locket (and Snapchat) actually define it,
     * rather than the looser "either direction counts" version this used to be.
     *
     * Still walks backward from today, and today is still allowed to be "pending" (the streak
     * doesn't break just because today isn't mutual yet) as long as yesterday was a genuine
     * mutual day — see [mutualDays].
     */
    fun compute(exchanges: List<StreakExchange>): Int {
        val days = mutualDays(exchanges)
        if (days.isEmpty()) return 0

        val today = LocalDate.now(ZoneOffset.UTC)
        var expected = when (days.first()) {
            today -> today
            today.minusDays(1) -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        for (day in days) {
            if (day == expected) {
                streak++
                expected = expected.minusDays(1)
            } else {
                break
            }
        }
        return streak
    }

    /** The most recent day both people actually exchanged a photo — used by ActivityService to
     * warn when a live streak's mutual exchange hasn't happened yet today. Not the same as "the
     * most recent exchange in either direction": one person sending today doesn't move this
     * forward until the other side has replied that same day. */
    fun mostRecentMutualDay(exchanges: List<StreakExchange>): LocalDate? = mutualDays(exchanges).firstOrNull()

    private fun mutualDays(exchanges: List<StreakExchange>): List<LocalDate> {
        val sentDays = exchanges.asSequence().filter { it.sentByMe }
            .mapTo(HashSet()) { it.timestamp.atZone(ZoneOffset.UTC).toLocalDate() }
        val receivedDays = exchanges.asSequence().filter { !it.sentByMe }
            .mapTo(HashSet()) { it.timestamp.atZone(ZoneOffset.UTC).toLocalDate() }
        return sentDays.intersect(receivedDays).sortedDescending()
    }
}
