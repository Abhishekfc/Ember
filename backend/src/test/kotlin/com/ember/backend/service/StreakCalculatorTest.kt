package com.ember.backend.service

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the rules the whole streak feature rests on. [StreakCalculator] is deliberately pure —
 * no Spring context, no database — so every case here is exact rather than approximate.
 *
 * Every test anchors on `today` captured once at the top rather than calling LocalDate.now
 * repeatedly, since the calculator reads the clock itself: capturing once keeps a test that
 * happens to straddle UTC midnight internally consistent instead of half-computed on each side.
 */
class StreakCalculatorTest {

    private val today: LocalDate = LocalDate.now(ZoneOffset.UTC)

    /** A mutual day: both sides sent. Noon keeps every timestamp comfortably inside its own UTC
     * day regardless of the clock when the suite runs. */
    private fun mutual(day: LocalDate): List<StreakExchange> = listOf(
        StreakExchange(day.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC), sentByMe = true),
        StreakExchange(day.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC), sentByMe = false),
    )

    private fun oneWay(day: LocalDate, sentByMe: Boolean): List<StreakExchange> = listOf(
        StreakExchange(day.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC), sentByMe = sentByMe),
    )

    // ---------------------------------------------------------------- compute: the basic rules

    @Test
    fun `no exchanges at all is no streak`() {
        assertEquals(0, StreakCalculator.compute(emptyList()))
    }

    @Test
    fun `one-directional days never count, however many there are`() {
        val exchanges = (0L..5L).flatMap { oneWay(today.minusDays(it), sentByMe = true) }
        assertEquals(0, StreakCalculator.compute(exchanges))
    }

    @Test
    fun `a mutual day today is a streak of one`() {
        assertEquals(1, StreakCalculator.compute(mutual(today)))
    }

    @Test
    fun `yesterday mutual with nothing yet today still counts - today is allowed to be pending`() {
        assertEquals(1, StreakCalculator.compute(mutual(today.minusDays(1))))
    }

    @Test
    fun `a gap of one full day breaks the streak`() {
        // Most recent mutual day is two days back: yesterday came and went with nothing.
        assertEquals(0, StreakCalculator.compute(mutual(today.minusDays(2))))
    }

    @Test
    fun `consecutive mutual days count up`() {
        val exchanges = (0L..4L).flatMap { mutual(today.minusDays(it)) }
        assertEquals(5, StreakCalculator.compute(exchanges))
    }

    @Test
    fun `only the run ending today or yesterday counts, not an older longer one`() {
        val recent = (0L..1L).flatMap { mutual(today.minusDays(it)) }
        val ancient = (10L..20L).flatMap { mutual(today.minusDays(it)) }
        assertEquals(2, StreakCalculator.compute(recent + ancient))
    }

    @Test
    fun `one side sending on a day does not make that day mutual`() {
        // Yesterday: both sent (mutual). Today: only I sent — today isn't mutual, but the streak
        // survives on yesterday, so this is 1, not 2.
        val exchanges = mutual(today.minusDays(1)) + oneWay(today, sentByMe = true)
        assertEquals(1, StreakCalculator.compute(exchanges))
    }

    // ------------------------------------------------- compute: restore / restoredThroughDate

    @Test
    fun `restoring a single missed day reconnects the chain`() {
        // Mutual through 2 days ago, missed yesterday, restoring bridges yesterday.
        val exchanges = (2L..5L).flatMap { mutual(today.minusDays(it)) }
        assertEquals(0, StreakCalculator.compute(exchanges), "precondition: broken without a restore")

        val restored = StreakCalculator.compute(exchanges, restoredThroughDate = today.minusDays(1))
        assertEquals(5, restored, "4 real days + the 1 bridged day")
    }

    @Test
    fun `restoring bridges a two-day gap, not just the single day named`() {
        // The regression this exists for: the restore window spans two days, so a restore bought
        // on its second day has TWO missed days behind it. Bridging only the named date would
        // leave the chain severed and hand back a streak of zero for a paid action.
        val exchanges = (3L..6L).flatMap { mutual(today.minusDays(it)) }
        assertEquals(0, StreakCalculator.compute(exchanges), "precondition: broken without a restore")

        val restored = StreakCalculator.compute(exchanges, restoredThroughDate = today.minusDays(1))
        assertEquals(6, restored, "4 real days + 2 bridged days")
    }

    @Test
    fun `restoring still works when new activity happened after the break`() {
        // Broke after day -4, then a fresh mutual day today. Restoring must rejoin the old chain
        // to that new activity rather than being confused by it.
        val exchanges = (4L..6L).flatMap { mutual(today.minusDays(it)) } + mutual(today)
        assertEquals(1, StreakCalculator.compute(exchanges), "precondition: only today counts")

        val restored = StreakCalculator.compute(exchanges, restoredThroughDate = today.minusDays(1))
        assertEquals(7, restored, "3 real old days + 3 bridged days + today")
    }

    @Test
    fun `a restore never reaches back past real history`() {
        // restoredThroughDate far older than anything on record must not invent an endless run of
        // bridged days — the walk stops at the earliest real mutual day.
        val exchanges = mutual(today.minusDays(1))
        val restored = StreakCalculator.compute(exchanges, restoredThroughDate = today.minusDays(400))
        assertEquals(1, restored, "the old restore date contributes nothing; only the real day counts")
    }

    @Test
    fun `a restore date already covered by real history changes nothing`() {
        val exchanges = (0L..2L).flatMap { mutual(today.minusDays(it)) }
        assertEquals(
            StreakCalculator.compute(exchanges),
            StreakCalculator.compute(exchanges, restoredThroughDate = today.minusDays(1)),
        )
    }

    // -------------------------------------------------------------------- mostRecentMutualDay

    @Test
    fun `most recent mutual day ignores one-directional sends`() {
        val exchanges = mutual(today.minusDays(3)) + oneWay(today, sentByMe = true)
        assertEquals(today.minusDays(3), StreakCalculator.mostRecentMutualDay(exchanges))
    }

    @Test
    fun `most recent mutual day is null when no day was ever mutual`() {
        assertNull(StreakCalculator.mostRecentMutualDay(oneWay(today, sentByMe = true)))
    }

    @Test
    fun `most recent mutual day counts a restored day`() {
        // The regression this exists for: leaving restoredThroughDate out here made every
        // deadline derived from it wrong for a freshly restored streak, silently dropping the
        // at-risk warning for the one friendship that had just been paid for.
        val exchanges = mutual(today.minusDays(3))
        assertEquals(
            today.minusDays(1),
            StreakCalculator.mostRecentMutualDay(exchanges, restoredThroughDate = today.minusDays(1)),
        )
    }

    @Test
    fun `real history still wins when it is newer than the restored day`() {
        val exchanges = mutual(today)
        assertEquals(
            today,
            StreakCalculator.mostRecentMutualDay(exchanges, restoredThroughDate = today.minusDays(1)),
        )
    }

    // --------------------------------------------------------------------------- at-risk rules

    @Test
    fun `no streak is never at risk`() {
        assertFalse(StreakCalculator.isAtRisk(streak = 0, mostRecentMutualDay = today.minusDays(1)))
    }

    @Test
    fun `a streak already kept up today is not at risk`() {
        assertFalse(StreakCalculator.isAtRisk(streak = 5, mostRecentMutualDay = today))
    }

    @Test
    fun `an already-broken streak is not at risk - it is past that`() {
        assertFalse(StreakCalculator.isAtRisk(streak = 5, mostRecentMutualDay = today.minusDays(2)))
    }

    @Test
    fun `at risk exactly when the deadline is inside the warning threshold`() {
        // Time-of-day dependent by nature, so this asserts the rule rather than a fixed answer:
        // with yesterday as the last mutual day, at-risk must agree with how much of today is
        // actually left.
        val midnight = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val hoursLeft = Duration.between(Instant.now(), midnight).toHours()
        assertEquals(
            hoursLeft <= 4,
            StreakCalculator.isAtRisk(streak = 3, mostRecentMutualDay = today.minusDays(1)),
            "threshold is 4 hours; $hoursLeft remain",
        )
    }

    // ------------------------------------------------------------------- current window deadline

    @Test
    fun `window deadline is UTC midnight tonight while today is still owed`() {
        val deadline = StreakCalculator.currentWindowDeadline(streak = 3, mostRecentMutualDay = today.minusDays(1))
        assertEquals(today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(), deadline)
    }

    @Test
    fun `no window deadline once today is already covered`() {
        assertNull(StreakCalculator.currentWindowDeadline(streak = 3, mostRecentMutualDay = today))
    }

    @Test
    fun `no window deadline without a live streak`() {
        assertNull(StreakCalculator.currentWindowDeadline(streak = 0, mostRecentMutualDay = today.minusDays(1)))
        assertNull(StreakCalculator.currentWindowDeadline(streak = 3, mostRecentMutualDay = null))
    }

    @Test
    fun `the at-risk window opens exactly the threshold before the deadline`() {
        val deadline = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val opensAt = StreakCalculator.atRiskWindowOpensAt(deadline)
        assertEquals(4, Duration.between(opensAt, deadline).toHours())
    }

    @Test
    fun `at-risk window opening is the moment isAtRisk starts being true`() {
        // The two have to agree, since one dates the warning event and the other decides whether
        // to show it at all — a mismatch would file the row at a time it wasn't yet true.
        val deadline = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val opensAt = StreakCalculator.atRiskWindowOpensAt(deadline)
        val isAtRiskNow = StreakCalculator.isAtRisk(streak = 3, mostRecentMutualDay = today.minusDays(1))
        assertEquals(!Instant.now().isBefore(opensAt), isAtRiskNow)
    }

    @Test
    fun `window deadline is unthresholded - it reports the real close, not the warning point`() {
        // The client applies its own "how close counts as urgent" threshold against its own clock
        // (see FriendsScreen), so this must stay the raw close of the window.
        val deadline = StreakCalculator.currentWindowDeadline(streak = 1, mostRecentMutualDay = today.minusDays(1))
        assertTrue(deadline!!.isAfter(Instant.now()), "tonight's midnight has not passed yet")
    }
}
