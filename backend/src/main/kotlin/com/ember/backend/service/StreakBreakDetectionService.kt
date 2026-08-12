package com.ember.backend.service

import com.ember.backend.model.FriendshipStatus
import com.ember.backend.model.FriendshipStreakState
import com.ember.backend.repository.FriendshipRepository
import com.ember.backend.repository.FriendshipStreakStateRepository
import com.ember.backend.repository.PhotoRecipientRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

// A restore is only offered for this long after a streak breaks — the deadline this job writes
// into FriendshipStreakState.restoreDeadline, which the restore endpoint itself re-checks
// server-side (never just implied by this job's own timing). Measured from the real break
// instant, not from when this job noticed, so the offer expires on schedule regardless of when
// detection actually ran. This is also the bound on how wide a gap a single restore can bridge —
// see StreakCalculator.compute's own note on restoredThroughDate.
private const val STREAK_RESTORE_WINDOW_DAYS = 2L

/** Runs once daily, shortly after UTC midnight — the same day boundary StreakCalculator itself
 * reasons in, so "yesterday" here always means the same calendar day the calculator would call
 * yesterday. Detects the one moment that actually matters: a friendship's streak going from a
 * real, positive value to zero. Streak itself is never stored (see StreakCalculator) — this is
 * the one place that keeps a "last known" value around specifically so that transition is
 * detectable at all, rather than recomputing from scratch with nothing to compare against.
 * Modeled after PhotoCleanupService: per-item `runCatching` so one bad friendship can't abort the
 * whole batch. */
@Service
class StreakBreakDetectionService(
    private val friendshipRepository: FriendshipRepository,
    private val photoRecipientRepository: PhotoRecipientRepository,
    private val friendshipStreakStateRepository: FriendshipStreakStateRepository,
    private val pushNotificationService: PushNotificationService,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // The per-friendship transaction boundary, applied explicitly instead of with @Transactional
    // on checkFriendship. That annotation is proxy-based, and detectBrokenStreaks reaches
    // checkFriendship as a plain call on `this` — a self-invocation never passes through the
    // Spring proxy, so the annotation was silently doing nothing at all despite the doc comment
    // claiming a real per-friendship transaction. This makes the boundary real regardless of how
    // the method is reached.
    private val transactionTemplate = TransactionTemplate(transactionManager)

    // The cron tick alone only fires if the backend happens to be alive at exactly 00:05 UTC —
    // fine for an always-on production deployment, but this backend is run manually from
    // IntelliJ during dev and is essentially never up at that exact moment. Running the same
    // check once on every startup means a break still gets caught (and notified) the next time
    // the backend happens to be running, rather than silently waiting for a cron tick that may
    // not land for days.
    @EventListener(ApplicationReadyEvent::class)
    fun runCatchUpOnStartup() {
        logger.info("Streak-break detection: running catch-up check on startup")
        detectBrokenStreaks()
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    fun detectBrokenStreaks() {
        val friendships = runCatching { friendshipRepository.findAllByStatus(FriendshipStatus.ACCEPTED) }
            .onFailure { logger.error("Streak-break detection: failed to load friendships", it) }
            .getOrNull() ?: return
        if (friendships.isEmpty()) return

        logger.info("Streak-break detection: checking {} accepted friendship(s)", friendships.size)
        friendships.forEach { friendship ->
            runCatching {
                checkFriendship(
                    friendshipId = friendship.id,
                    requesterId = friendship.requester.id,
                    addresseeId = friendship.addressee.id,
                    requesterDisplayName = friendship.requester.displayName,
                    addresseeDisplayName = friendship.addressee.displayName,
                )
            }.onFailure { logger.error("Streak-break detection failed for friendshipId={}", friendship.id, it) }
        }
    }

    /** Own transaction per friendship, same reasoning as PhotoCleanupService's per-row deletes —
     * one friendship's failure shouldn't roll back state already correctly updated for every
     * other friendship processed before it in the same batch. */
    fun checkFriendship(
        friendshipId: UUID,
        requesterId: UUID,
        addresseeId: UUID,
        requesterDisplayName: String,
        addresseeDisplayName: String,
    ) = transactionTemplate.executeWithoutResult {
        // Exchanges tagged relative to the requester — mutual-day computation is symmetric
        // either way (it's a set intersection of both sides' send-days), so this is as valid a
        // reference point as the addressee's own perspective would be.
        val exchanges = photoRecipientRepository.findExchangeTimestampsBatch(requesterId, listOf(addresseeId))
            .map { StreakExchange(it.createdAt, it.sentByMe) }

        val existingState = friendshipStreakStateRepository.findById(friendshipId)
        val state = existingState.orElse(FriendshipStreakState(friendshipId = friendshipId))
        val currentStreak = StreakCalculator.compute(exchanges, state.restoredThroughDate)

        // The transition that actually matters: a real streak just hit zero. A friendship with
        // no streak history at all (0 -> 0, e.g. two people who've never both sent the same day)
        // is not a break — nothing was ever going that could lapse.
        //
        // The second half of this condition covers a friendship being checked for the very first
        // time (no state row yet, so there's nothing to compare against — lastKnownStreak defaults
        // to 0) where the streak had already broken before this job ever got a chance to observe it
        // alive. Without this, that break would be silently absorbed as "0 -> 0, no change" forever,
        // since a state row this method itself just created can never again look "new" on a later
        // run. mostRecentMutualDay(exchanges) != null is what distinguishes "a real streak existed
        // and lapsed" from "these two have just never exchanged a mutual day at all".
        val alreadyBrokenBeforeFirstCheck = existingState.isEmpty && currentStreak == 0 &&
            StreakCalculator.mostRecentMutualDay(exchanges) != null
        if ((state.lastKnownStreak > 0 && currentStreak == 0) || alreadyBrokenBeforeFirstCheck) {
            notifyBreak(friendshipId, requesterId, addresseeId, requesterDisplayName, addresseeDisplayName, exchanges, state)
        }

        // A live streak means whatever break this row last recorded is over and done with. Left
        // set, a stale brokenAt keeps driving ActivityService's own STREAK_BROKEN row forever —
        // "your streak with X broke" showing indefinitely underneath a friendship that has since
        // built a brand-new streak. restoredThroughDate is deliberately NOT cleared here: a
        // restored day stays part of the very chain this live streak is counted from, so clearing
        // it would silently collapse the streak it's holding up.
        if (currentStreak > 0) {
            state.brokenAt = null
            state.restoreDeadline = null
        }

        state.lastKnownStreak = currentStreak
        state.updatedAt = Instant.now()
        friendshipStreakStateRepository.save(state)
    }

    private fun notifyBreak(
        friendshipId: UUID,
        requesterId: UUID,
        addresseeId: UUID,
        requesterDisplayName: String,
        addresseeDisplayName: String,
        exchanges: List<StreakExchange>,
        state: FriendshipStreakState,
    ) {
        // The day that actually lapsed is the one right after the last day both sides exchanged —
        // derived from real history, never from "yesterday relative to whenever this job happened
        // to run". Those two only agree when the job runs on time; a run that's even a day late
        // (the backend was down, or the startup catch-up above is what found the break) would
        // otherwise blame the wrong day, which then propagates straight into what a restore
        // bridges — see FriendService.restoreStreak, which derives the missed day back out of
        // brokenAt. Null means no mutual day ever existed, so no streak could have lapsed.
        val lastMutualDay = StreakCalculator.mostRecentMutualDay(exchanges, state.restoredThroughDate) ?: return
        val missedDay = lastMutualDay.plusDays(1)
        val requesterSentOnMissedDay = exchanges.any {
            it.sentByMe && it.timestamp.atZone(ZoneOffset.UTC).toLocalDate() == missedDay
        }
        val addresseeSentOnMissedDay = exchanges.any {
            !it.sentByMe && it.timestamp.atZone(ZoneOffset.UTC).toLocalDate() == missedDay
        }

        // Notify whoever didn't send on the day it actually lapsed — they're the one "Restore
        // streak" is actionable for right now (the other side already held up their end). If
        // neither sent, nobody uniquely let it lapse, so both get told.
        val recipients = when {
            requesterSentOnMissedDay && !addresseeSentOnMissedDay -> listOf(addresseeId to requesterDisplayName)
            addresseeSentOnMissedDay && !requesterSentOnMissedDay -> listOf(requesterId to addresseeDisplayName)
            else -> listOf(addresseeId to requesterDisplayName, requesterId to addresseeDisplayName)
        }

        // The real instant the streak stopped counting — the UTC midnight that ends the missed
        // day, which is exactly when StreakCalculator.compute starts returning zero for it (a
        // streak survives while its most recent mutual day is still "yesterday", and only drops
        // once that day is two days back). Anchoring on this rather than Instant.now() is what
        // makes the restore window mean "24h from when it actually broke" instead of "24h from
        // whenever this job noticed", which would hand out a fresh, full window for a break that
        // is genuinely days old.
        val brokenAt = missedDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val deadline = brokenAt.plus(STREAK_RESTORE_WINDOW_DAYS, ChronoUnit.DAYS)
        state.brokenAt = brokenAt
        state.restoreDeadline = deadline
        // Cleared so a *new* break is restorable again — FriendService gates the restore offer on
        // this being null, and the previous break's bridged day is no longer holding anything up
        // (the chain it supported is what just broke).
        state.restoredThroughDate = null

        // Recorded above either way so Activity can still show that the streak broke, but there's
        // nothing actionable to push a notification about once the window has already closed —
        // the client would show a "Restore streak" action that the server would then refuse.
        if (deadline.isBefore(Instant.now())) {
            logger.info(
                "Streak broken but restore window already closed: friendshipId={}, brokenAt={}, deadline={}",
                friendshipId, brokenAt, deadline,
            )
            return
        }

        recipients.forEach { (recipientId, friendDisplayName) ->
            pushNotificationService.notifyStreakBroken(
                friendshipId = friendshipId,
                friendDisplayName = friendDisplayName,
                restoreDeadlineEpochSeconds = deadline.epochSecond,
                recipientUserId = recipientId,
            )
        }
        logger.info("Streak broken: friendshipId={}, notified {} recipient(s)", friendshipId, recipients.size)
    }
}
