package com.ember.backend.service

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** One photo exchange between the current user and a specific friend, tagged with which
 * direction it went — a streak day now requires activity in both directions (see
 * [StreakCalculator.compute]), so the direction has to travel with the timestamp. */
data class StreakExchange(val timestamp: Instant, val sentByMe: Boolean)

// How close to the UTC-midnight deadline a streak has to be before it counts as genuinely "at
// risk" — see isAtRisk's own doc comment for why this exists at all, not just "yesterday was
// mutual, today isn't yet" on its own.
private const val STREAK_AT_RISK_THRESHOLD_HOURS = 4L

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
     *
     * [restoredThroughDate] is the one lever an Ember Gold restore pulls: the last day of a gap
     * (see [com.ember.backend.model.FriendshipStreakState]) that this treats as mutual even though
     * real exchange history says otherwise. It bridges the whole contiguous run of missed days
     * ending on that date — not just that one date — because the restore window spans more than a
     * single day (see StreakBreakDetectionService.STREAK_RESTORE_WINDOW_DAYS): someone restoring
     * on the second day has two missed days between their last real exchange and yesterday, and
     * bridging only one of them would leave the chain still severed, quietly returning a streak of
     * zero for an action they just paid for. Walking back from [restoredThroughDate] and stopping
     * at the first real mutual day fills exactly that gap and nothing more.
     *
     * This is deliberately the only thing a restore touches — real exchange rows are never
     * fabricated or edited, so exchange history stays ground truth and a restore is just one more
     * input to an otherwise-pure calculation, not a mutation of the past.
     */
    fun compute(exchanges: List<StreakExchange>, restoredThroughDate: LocalDate? = null): Int {
        val realDays = mutualDays(exchanges)
        val days = if (restoredThroughDate != null) {
            (realDays + bridgedDays(realDays, restoredThroughDate)).distinct().sortedDescending()
        } else {
            realDays
        }
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
     * forward until the other side has replied that same day.
     *
     * [restoredThroughDate] participates for exactly the same reason it does in [compute]: a
     * restored day is a genuine part of the chain from that point on, so a caller asking "when
     * did this streak last have a mutual day" has to see it too. Leaving it out made every
     * deadline derived from this wrong for a freshly restored streak — [currentWindowDeadline]
     * would look for "yesterday" and find only the pre-break day, conclude there was no live
     * window at all, and silently drop the at-risk warning for the one friendship that had just
     * been paid for. */
    fun mostRecentMutualDay(exchanges: List<StreakExchange>, restoredThroughDate: LocalDate? = null): LocalDate? {
        val realMostRecent = mutualDays(exchanges).firstOrNull()
        return when {
            restoredThroughDate == null -> realMostRecent
            realMostRecent == null -> restoredThroughDate
            else -> maxOf(realMostRecent, restoredThroughDate)
        }
    }

    /** Whether a streak is close enough to its UTC-midnight deadline to actually warn about —
     * "yesterday was mutual, today isn't yet" on its own is true for the entire day (potentially
     * 20+ hours before anything is actually at stake), which is what made both the Friends list's
     * hourglass and ActivityService's own STREAK_EXPIRING event show up far earlier than the name
     * "expiring" implied. Real urgency only starts once fewer than [STREAK_AT_RISK_THRESHOLD_HOURS]
     * remain. The one shared home for this check — both callers used to compute the same window
     * independently, which is exactly how they could (and did) drift apart. */
    fun isAtRisk(streak: Int, mostRecentMutualDay: LocalDate?): Boolean {
        if (streak <= 0 || mostRecentMutualDay == null) return false
        val today = LocalDate.now(ZoneOffset.UTC)
        if (mostRecentMutualDay != today.minusDays(1)) return false
        val midnightUtc = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val hoursLeft = Duration.between(Instant.now(), midnightUtc).toHours()
        return hoursLeft <= STREAK_AT_RISK_THRESHOLD_HOURS
    }

    /** The instant the "about to expire" warning becomes true for a window closing at [deadline] —
     * i.e. [STREAK_AT_RISK_THRESHOLD_HOURS] before it. Exposed so a caller surfacing that warning
     * as a timestamped event can date it to when the warning actually started, rather than either
     * re-stamping it "now" on every fetch (which makes it permanently read as "just now") or
     * anchoring it to the start of the day (which buries a warning that only appears in its final
     * hours underneath everything else that happened earlier that day). */
    fun atRiskWindowOpensAt(deadline: Instant): Instant =
        deadline.minus(STREAK_AT_RISK_THRESHOLD_HOURS, ChronoUnit.HOURS)

    /** The instant today's mutual-exchange window actually closes, or null if there's no live
     * streak whose window is still open (either no streak, or today's exchange already happened).
     * Unlike [isAtRisk], this doesn't apply the "how close counts as urgent" threshold at all —
     * it's meant for FriendService, which hands this raw deadline to the client and lets *that*
     * side decide, live against its own clock, whether it's currently within the risk window.
     * That's deliberate: a boolean computed once at request time is only ever correct at that
     * instant — cached or offline data (FriendsScreen's own LocalListCache copy) would keep
     * showing whatever was true at the last fetch, drifting wrong the longer the device stays
     * offline, where a deadline timestamp stays correct for as long as the device's own clock is. */
    fun currentWindowDeadline(streak: Int, mostRecentMutualDay: LocalDate?): Instant? {
        if (streak <= 0 || mostRecentMutualDay == null) return null
        val today = LocalDate.now(ZoneOffset.UTC)
        if (mostRecentMutualDay != today.minusDays(1)) return null
        return today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
    }

    /** The contiguous run of non-mutual days ending at [restoredThroughDate], walking backwards
     * and stopping at the first day real history already covers — the exact gap a restore is
     * paying to close. Bounded on both ends by construction: it terminates either at a real
     * mutual day or at the earliest one on record, so it can never run away even if
     * [restoredThroughDate] is far in the past or the future. */
    private fun bridgedDays(realDays: List<LocalDate>, restoredThroughDate: LocalDate): List<LocalDate> {
        // No real history at all means there's no chain for a bridge to reconnect to — treat it
        // as the single day it names rather than inventing a run of days out of nothing.
        val earliestReal = realDays.lastOrNull() ?: return listOf(restoredThroughDate)
        val realSet = realDays.toHashSet()
        return generateSequence(restoredThroughDate) { it.minusDays(1) }
            .takeWhile { it !in realSet && it.isAfter(earliestReal) }
            .toList()
    }

    private fun mutualDays(exchanges: List<StreakExchange>): List<LocalDate> {
        val sentDays = exchanges.asSequence().filter { it.sentByMe }
            .mapTo(HashSet()) { it.timestamp.atZone(ZoneOffset.UTC).toLocalDate() }
        val receivedDays = exchanges.asSequence().filter { !it.sentByMe }
            .mapTo(HashSet()) { it.timestamp.atZone(ZoneOffset.UTC).toLocalDate() }
        return sentDays.intersect(receivedDays).sortedDescending()
    }
}
