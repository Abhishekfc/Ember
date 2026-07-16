package com.ember.backend.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object StreakCalculator {

    /**
     * Consecutive-day streak, Snapchat-style: counts unbroken days (UTC) with at least one
     * exchanged photo, walking backward from today. Today is allowed to be "pending" (streak
     * doesn't break just because neither side has sent yet today) as long as yesterday is unbroken.
     */
    fun compute(exchangeTimestamps: List<Instant>): Int {
        if (exchangeTimestamps.isEmpty()) return 0
        val days = exchangeTimestamps.map { it.atZone(ZoneOffset.UTC).toLocalDate() }
            .distinct()
            .sortedDescending()

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
}
