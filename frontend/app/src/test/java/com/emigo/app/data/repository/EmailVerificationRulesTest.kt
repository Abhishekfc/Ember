package com.emigo.app.data.repository

import com.emigo.app.data.remote.dto.UserProfileDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailVerificationRulesTest {

    private fun profile(createdAt: String? = null, verificationRequired: Boolean = false) = UserProfileDto(
        userId = "user-1",
        displayName = "Test User",
        username = "test",
        email = "test@example.com",
        profilePhotoUrl = null,
        createdAt = createdAt,
        emailVerificationRequired = verificationRequired,
    )

    @Test
    fun gracePeriodIsTenMinutes() {
        // Must match EmailVerificationExpiryService on the backend.
        assertEquals(10 * 60 * 1000L, EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS)
    }

    @Test
    fun theServerFlagAloneDecidesWhetherVerificationIsNeeded() {
        assertTrue(needsEmailVerification(profile(verificationRequired = true)))
        assertFalse(needsEmailVerification(profile(verificationRequired = false)))
    }

    @Test
    fun anOldCachedProfileWithoutTheFlagDoesNotLockTheUserOut() {
        // The DTO default is the safe fallback for profiles cached before the field existed.
        assertFalse(needsEmailVerification(profile()))
    }

    @Test
    fun theDeadlineIsCountedFromWhenTheAccountWasCreated() {
        val createdAt = "2026-01-01T12:00:00Z"
        val expected = Instant.parse(createdAt).toEpochMilli() + EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS
        assertEquals(expected, verificationDeadlineFor(profile(createdAt = createdAt)))
    }

    @Test
    fun reopeningTheScreenNeverResetsTheCountdown() {
        val p = profile(createdAt = "2026-01-01T12:00:00Z")
        assertEquals(verificationDeadlineFor(p), verificationDeadlineFor(p))
    }

    @Test
    fun aMissingCreatedAtFallsBackToAFreshWindowFromNow() {
        assertFreshWindow(profile(createdAt = null))
    }

    @Test
    fun anUnparseableCreatedAtFallsBackToAFreshWindowFromNow() {
        assertFreshWindow(profile(createdAt = "not a date"))
    }

    private fun assertFreshWindow(p: UserProfileDto) {
        val before = System.currentTimeMillis()
        val deadline = verificationDeadlineFor(p)
        val after = System.currentTimeMillis()
        assertTrue(deadline >= before + EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS)
        assertTrue(deadline <= after + EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS)
    }
}
