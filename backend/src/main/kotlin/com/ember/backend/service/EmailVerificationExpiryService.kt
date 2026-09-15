package com.ember.backend.service

import com.ember.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

// Must stay in step with the Android client's own EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS, which
// is what the countdown on the verification screen actually shows. Also the single source of
// truth FirebaseAuthenticationFilter itself imports — see that filter's own doc comment for why
// both of them agreeing on this exact instant is what makes the cutoff actually hard, rather than
// two independent guesses that can disagree.
val UNVERIFIED_ACCOUNT_GRACE_PERIOD: Duration = Duration.ofMinutes(10)

/**
 * Deletes a brand-new account outright if it never verifies its email within
 * [UNVERIFIED_ACCOUNT_GRACE_PERIOD] of being created — the backend half of the countdown shown on
 * the Android client's own VerifyEmailStep. Chosen deliberately short (not the multi-day grace
 * period an "abandoned signup" cleanup would otherwise use) specifically so someone who typed an
 * email they don't control — the actual problem this whole feature exists to prevent — can't leave
 * that email permanently unusable by its real owner for long.
 *
 * No live Firebase check here, deliberately, unlike an earlier version of this class. Whether
 * verification happened at all no longer matters here, only whether it happened *before* this
 * deadline — and FirebaseAuthenticationFilter is now the only place [User.emailVerificationRequired]
 * ever gets cleared, and it only clears it for a request that arrives before this same deadline
 * (see its own doc comment). So by construction, every row this query still returns is one that
 * was never cleared in time, and there's nothing left worth checking before deleting it. An
 * earlier version of this class *did* check live and let a late-but-genuine verification survive
 * anyway — found by testing exactly that: verifying only after the on-screen timer had already
 * failed still left the account alive and permanently exempt from ever being swept again, once
 * that live check cleared the flag for good. That's the "genuinely honest person who's a little
 * slow to check their inbox" tradeoff this class's very first version explicitly accepted; this
 * version doesn't, on request — the deadline shown on screen is now the real deadline, with no
 * grace once it's passed.
 *
 * Runs once a day (staggered after StreakBreakDetectionService/PhotoCleanupService's own midnight
 * jobs), not every 10 seconds like an earlier version of this class. Access was already cut off
 * exactly at the deadline regardless (see FirebaseAuthenticationFilter), so this interval only
 * ever affects how long a technically-inaccessible row keeps existing, never who can get in — the
 * query cost argument an earlier version of this doc comment made for running every 10 seconds was
 * real (a tiny, index-backed query, see the V14 migration) but missed the actual cost: Neon bills
 * for wall-clock time its compute is awake, not query cost, and *any* connection — including one
 * that finds nothing to do — resets its idle-suspend timer. A job every 10 seconds never gave Neon
 * a large enough gap to ever suspend, which is the same "billed 24 hours a day for an app with
 * almost no traffic" problem the datasource's own hikari.minimum-idle=0 setting exists to prevent
 * (see application.yml), just via a different path. Once a day closes that gap without changing
 * anything about who can access the app.
 */
@Service
class EmailVerificationExpiryService(
    private val userRepository: UserRepository,
    private val userService: UserService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 15 0 * * *", zone = "UTC")
    fun expireUnverifiedAccounts() {
        val cutoff = Instant.now().minus(UNVERIFIED_ACCOUNT_GRACE_PERIOD)
        val candidates = runCatching { userRepository.findByEmailVerificationRequiredTrueAndCreatedAtBefore(cutoff) }
            .onFailure { logger.error("Email-verification expiry: failed to load candidates", it) }
            .getOrNull() ?: return
        if (candidates.isEmpty()) return

        candidates.forEach { user ->
            logger.info(
                "Email-verification expiry: deleting account userId={} — never verified before its own {} deadline",
                user.id,
                UNVERIFIED_ACCOUNT_GRACE_PERIOD,
            )
            runCatching { userService.deleteAccount(user.id) }
                .onFailure { logger.error("Email-verification expiry: failed to delete userId={}", user.id, it) }
        }
    }
}
