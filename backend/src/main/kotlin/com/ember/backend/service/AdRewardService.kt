package com.ember.backend.service

import com.ember.backend.config.AdProperties
import com.ember.backend.exception.AdRewardRequiredException
import com.ember.backend.model.FriendshipStatus
import com.ember.backend.repository.AdRewardRepository
import com.ember.backend.repository.FriendshipRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** How long a watched ad stays good for a restore. Long enough to ride out a slow callback or a
 * flaky connection right after the ad, short enough that a reward isn't banked for later. */
private val REWARD_VALIDITY: Duration = Duration.ofMinutes(15)

/** How long finished records are kept, only so a repeated callback is still recognised. Callbacks
 * older than a day are refused outright (see [AdRewardVerifier]), so a week is more than enough. */
private val RECORD_RETENTION: Duration = Duration.ofDays(7)

/** What happened to a request to the reward callback. */
enum class RecordOutcome {
    /** A new reward was saved. */
    RECORDED,

    /** Google already told us about this ad; nothing to do. */
    DUPLICATE,

    /** Genuine, but about a friendship that isn't the user's: nothing is saved, and Google needn't
     * try again. */
    IGNORED,

    /** Not from Google, or not for one of our ads. */
    INVALID,
}

/** Lets someone without Emigo Gold restore a broken streak by watching ads. The app never says an
 * ad was watched: Google does, through [record], and a restore then spends those records. */
@Service
class AdRewardService(
    private val adRewardRepository: AdRewardRepository,
    private val friendshipRepository: FriendshipRepository,
    private val verifier: AdRewardVerifier,
    private val adProperties: AdProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun record(rawQuery: String?, now: Instant = Instant.now()): RecordOutcome {
        val reward = verifier.verify(rawQuery, now) ?: return RecordOutcome.INVALID

        val friendship = friendshipRepository.findById(reward.friendshipId).orElse(null)
        val isTheirs = friendship != null &&
            friendship.status == FriendshipStatus.ACCEPTED &&
            (friendship.requester.id == reward.userId || friendship.addressee.id == reward.userId)
        if (!isTheirs) return RecordOutcome.IGNORED

        val added = adRewardRepository.insertIfAbsent(reward.transactionId, reward.userId, reward.friendshipId, now)
        return if (added == 1) RecordOutcome.RECORDED else RecordOutcome.DUPLICATE
    }

    /** Spends the fresh rewards one restore costs ([AdProperties.restoreAdsRequired]) for this user
     * and friendship, all or none. Returns when the restore may go ahead; otherwise throws
     * [AdRewardRequiredException] saying how many ads it takes and how many are on record, and
     * anything already spent in this call is given back (the throw rolls the transaction back). */
    @Transactional
    fun spendRestoreRewards(userId: UUID, friendshipId: UUID, now: Instant = Instant.now()) {
        val required = adProperties.restoreAdsRequired.coerceAtLeast(1)
        val candidates = adRewardRepository.findUnspentIds(userId, friendshipId, now.minus(REWARD_VALIDITY), PageRequest.of(0, required))
        if (candidates.size < required) throw AdRewardRequiredException(required, candidates.size)
        // markSpent is the real gate: if a second restore took one of these a moment ago it
        // returns 0, and this restore is refused rather than going ahead short of an ad.
        val spent = candidates.count { adRewardRepository.markSpent(it, now) == 1 }
        if (spent < required) throw AdRewardRequiredException(required, spent)
    }

    @Scheduled(cron = "0 25 0 * * *", zone = "UTC")
    @Transactional
    fun deleteOldRecords() {
        val deleted = adRewardRepository.deleteOlderThan(Instant.now().minus(RECORD_RETENTION))
        if (deleted > 0) logger.info("Deleted {} old ad reward records", deleted)
    }
}
