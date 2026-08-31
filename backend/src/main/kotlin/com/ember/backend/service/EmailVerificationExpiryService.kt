package com.ember.backend.service

import com.ember.backend.repository.UserRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

// Must stay in step with the Android client's own EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS, which
// is what the countdown on the verification screen actually shows. This one is the only copy that
// deletes anything.
private val UNVERIFIED_ACCOUNT_GRACE_PERIOD: Duration = Duration.ofMinutes(10)

/**
 * Deletes a brand-new account outright if it never verifies its email within
 * [UNVERIFIED_ACCOUNT_GRACE_PERIOD] of being created — the backend half of the countdown shown on
 * the Android client's own VerifyEmailStep. Chosen deliberately short (not the multi-day grace
 * period an "abandoned signup" cleanup would otherwise use) specifically so someone who typed an
 * email they don't control — the actual problem this whole feature exists to prevent — can't leave
 * that email permanently unusable by its real owner for long. The real cost of that choice: a
 * genuinely honest person who's just a little slow to check their inbox (a different device, a
 * distraction, a delayed email) loses their in-progress signup and has to start over — a real,
 * accepted tradeoff, not an oversight.
 *
 * Runs every minute, not on a daily cron like StreakBreakDetectionService — a 10-minute grace
 * period backed by a check that only runs once a day would never actually fire within it.
 */
@Service
class EmailVerificationExpiryService(
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val firebaseApp: FirebaseApp?,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 60_000)
    fun expireUnverifiedAccounts() {
        val app = firebaseApp ?: return
        val cutoff = Instant.now().minus(UNVERIFIED_ACCOUNT_GRACE_PERIOD)
        val candidates = runCatching { userRepository.findByEmailVerificationRequiredTrueAndCreatedAtBefore(cutoff) }
            .onFailure { logger.error("Email-verification expiry: failed to load candidates", it) }
            .getOrNull() ?: return
        if (candidates.isEmpty()) return

        candidates.forEach { user ->
            val uid = user.firebaseUid ?: return@forEach
            // A real, live check against Firebase, not just this row's own age — see
            // UserRepository.findByEmailVerificationRequiredTrueAndCreatedAtBefore's own doc
            // comment for why emailVerificationRequired alone can never answer "has this actually
            // been verified since." Anything unreadable here (a Firebase hiccup, the identity
            // already gone some other way) is treated as "leave it alone this cycle" rather than
            // risk deleting an account that's actually fine — there's another chance to check
            // again a minute later regardless.
            val isVerified = runCatching { FirebaseAuth.getInstance(app).getUser(uid).isEmailVerified }
                .onFailure { logger.warn("Email-verification expiry: couldn't check Firebase for uid={}", uid, it) }
                .getOrNull() ?: return@forEach
            if (isVerified) {
                // Verified after all, so it survives — and stops being a candidate from here on.
                // FirebaseAuthenticationFilter clears this same flag the moment a verified token
                // shows up on any request, which covers almost every account; this covers the one
                // it can't, someone who clicked the link but hasn't opened the app since. Without
                // clearing it here too, exactly those accounts would be re-checked against
                // Firebase every single minute, forever.
                runCatching { userRepository.clearEmailVerificationRequired(user.id) }
                    .onFailure { logger.warn("Email-verification expiry: couldn't clear flag for userId={}", user.id, it) }
                return@forEach
            }
            logger.info(
                "Email-verification expiry: deleting never-verified account userId={}, older than {}",
                user.id,
                UNVERIFIED_ACCOUNT_GRACE_PERIOD,
            )
            runCatching { userService.deleteAccount(user.id) }
                .onFailure { logger.error("Email-verification expiry: failed to delete userId={}", user.id, it) }
        }
    }
}
