package com.ember.backend.service

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Decides whether a server restart needs to re-check every friendship's streak. It must skip when
 * the nightly check already covered everything (so deploys cost the database nothing), and must
 * run whenever anything could have been missed. */
class StreakCatchUpTest {

    // The most recent nightly run is 00:05 UTC, today's or yesterday's.

    @Test
    fun afterTodaysRunTimeItIsTodaysRun() {
        assertEquals(Instant.parse("2026-10-10T00:05:00Z"), mostRecentNightlyRun(Instant.parse("2026-10-10T11:14:08Z")))
        assertEquals(Instant.parse("2026-10-10T00:05:00Z"), mostRecentNightlyRun(Instant.parse("2026-10-10T23:59:59Z")))
    }

    @Test
    fun beforeTodaysRunTimeItIsYesterdaysRun() {
        assertEquals(Instant.parse("2026-10-09T00:05:00Z"), mostRecentNightlyRun(Instant.parse("2026-10-10T00:00:00Z")))
        assertEquals(Instant.parse("2026-10-09T00:05:00Z"), mostRecentNightlyRun(Instant.parse("2026-10-10T00:04:59Z")))
    }

    @Test
    fun atTheExactRunTimeItIsThatRun() {
        assertEquals(Instant.parse("2026-10-10T00:05:00Z"), mostRecentNightlyRun(Instant.parse("2026-10-10T00:05:00Z")))
    }

    @Test
    fun itWorksAcrossMonthAndYearEnds() {
        assertEquals(Instant.parse("2026-09-30T00:05:00Z"), mostRecentNightlyRun(Instant.parse("2026-10-01T00:01:00Z")))
        assertEquals(Instant.parse("2025-12-31T00:05:00Z"), mostRecentNightlyRun(Instant.parse("2026-01-01T00:01:00Z")))
    }

    // The decision.

    @Test
    fun whenEverythingWasCheckedSinceTheNightlyRunTheStartUpCheckIsSkipped() {
        assertFalse(catchUpIsNeeded(acceptedFriendships = 55, checkedFriendships = 55, notCheckedSince = 0))
    }

    @Test
    fun anOldCheckMeansTheNightlyRunWasMissedSoItRuns() {
        // The server was down at 00:05 UTC: those friendships were last checked yesterday.
        assertTrue(catchUpIsNeeded(acceptedFriendships = 55, checkedFriendships = 55, notCheckedSince = 55))
    }

    @Test
    fun evenOneFriendshipNotCheckedSinceMakesItRun() {
        // A run that stopped part way leaves a few behind.
        assertTrue(catchUpIsNeeded(acceptedFriendships = 55, checkedFriendships = 55, notCheckedSince = 1))
    }

    @Test
    fun aFriendshipThatWasNeverCheckedMakesItRun() {
        // Newly accepted: no row yet, so fewer rows than friendships.
        assertTrue(catchUpIsNeeded(acceptedFriendships = 56, checkedFriendships = 55, notCheckedSince = 0))
    }

    @Test
    fun noFriendshipsAtAllNeedsNoCheck() {
        assertFalse(catchUpIsNeeded(acceptedFriendships = 0, checkedFriendships = 0, notCheckedSince = 0))
    }

    @Test
    fun moreRowsThanAcceptedFriendshipsIsFine() {
        // Rows can outnumber accepted friendships (a pending or removed one still has its row): not a reason to run.
        assertFalse(catchUpIsNeeded(acceptedFriendships = 50, checkedFriendships = 55, notCheckedSince = 0))
    }
}
