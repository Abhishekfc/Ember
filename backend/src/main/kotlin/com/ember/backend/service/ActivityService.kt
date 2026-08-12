package com.ember.backend.service

import com.ember.backend.dto.ActivityEvent
import com.ember.backend.dto.ActivityEventType
import com.ember.backend.dto.ActivityLastSeen
import com.ember.backend.dto.Page
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.FriendshipStatus
import com.ember.backend.repository.FriendshipRepository
import com.ember.backend.repository.FriendshipStreakStateRepository
import com.ember.backend.repository.PhotoRecipientRepository
import com.ember.backend.repository.UserRepository
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val RECENT_PHOTOS_LIMIT = 20
// Reaction feature disabled — see PhotoReactionService's own comment.
// private const val RECENT_REACTIONS_LIMIT = 20

@Service
class ActivityService(
    private val friendshipRepository: FriendshipRepository,
    private val photoRecipientRepository: PhotoRecipientRepository,
    // private val photoReactionRepository: PhotoReactionRepository,
    private val userRepository: UserRepository,
    private val r2StorageService: R2StorageService,
    private val cacheManager: CacheManager,
    private val friendshipStreakStateRepository: FriendshipStreakStateRepository,
) {

    fun getLastSeen(userId: UUID): ActivityLastSeen {
        val user = userRepository.findById(userId).orElseThrow { ResourceNotFoundException("User not found") }
        return ActivityLastSeen(user.activityLastSeenAt)
    }

    @Transactional
    fun markSeen(userId: UUID): ActivityLastSeen {
        val user = userRepository.findById(userId).orElseThrow { ResourceNotFoundException("User not found") }
        user.activityLastSeenAt = Instant.now()
        userRepository.save(user)
        return ActivityLastSeen(user.activityLastSeenAt)
    }

    /** Cached per-user, same read-bypass-but-rewrite-on-[forceRefresh] pattern as
     * [FriendService.getFriends] — this assembles from three separate repository queries plus a
     * per-friend streak calculation, so on a feed with several friends it's the most expensive
     * of the three cached endpoints to rebuild from scratch on every tab open. Evicted wherever
     * something that can produce a new event happens: a photo upload (PhotoService), a friend
     * request sent or accepted (FriendService) — the 30s TTL is the safety net for the rest. */
    fun getActivity(userId: UUID, offset: Int = 0, limit: Int = 30, forceRefresh: Boolean = false): Page<ActivityEvent> {
        val cache = cacheManager.getCache("activity")
        val cacheKey = userId.toString()
        val events = if (!forceRefresh) {
            // See PhotoService.getFeed: a cache read failure (e.g. an empty-list result that
            // GenericJackson2JsonRedisSerializer doesn't round-trip reliably) must be treated as
            // a miss, not allowed to fail the request.
            runCatching { cache?.get(cacheKey, List::class.java) }.getOrNull()?.let {
                @Suppress("UNCHECKED_CAST")
                it as List<ActivityEvent>
            } ?: computeActivity(userId).also { cache?.put(cacheKey, it) }
        } else {
            computeActivity(userId).also { cache?.put(cacheKey, it) }
        }

        // Paginated in memory, same reasoning as FriendService.getFriends — the full,
        // already-capped-at-ACTIVITY_FEED_LIMIT list is what's cached and evicted as a unit;
        // this just slices the requested page out of it.
        val page = events.drop(offset).take(limit)
        return Page(items = page, hasMore = offset + limit < events.size)
    }

    private fun computeActivity(userId: UUID): List<ActivityEvent> {
        val events = mutableListOf<ActivityEvent>()

        // Consecutive photos from the same sender collapse into one entry ("Priya sent you 3
        // photos") instead of three near-identical rows back to back — the rows are already
        // sorted newest-first, so this only merges genuinely back-to-back sends, not every photo
        // that friend has ever sent.
        val photoRows = photoRecipientRepository.findRecentReceived(userId, RECENT_PHOTOS_LIMIT)
        var i = 0
        while (i < photoRows.size) {
            val senderId = photoRows[i].senderId
            var j = i + 1
            while (j < photoRows.size && photoRows[j].senderId == senderId) j++
            val group = photoRows.subList(i, j)
            val latest = group.first()
            val count = group.size
            events += ActivityEvent(
                type = ActivityEventType.PHOTO_RECEIVED,
                actorId = latest.senderId,
                actorDisplayName = latest.senderDisplayName,
                actorProfilePhotoUrl = latest.senderProfilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
                message = if (count == 1) {
                    "${latest.senderDisplayName} sent you a photo"
                } else {
                    "${latest.senderDisplayName} sent you $count photos"
                },
                createdAt = latest.createdAt,
                photoUrl = r2StorageService.publicUrl(latest.storageKey),
            )
            i = j
        }

        // Reaction feature disabled — see PhotoReactionService's own comment.
        // photoReactionRepository.findRecentReactionsReceived(userId, RECENT_REACTIONS_LIMIT).forEach { row ->
        //     events += ActivityEvent(
        //         type = ActivityEventType.PHOTO_REACTION,
        //         actorId = row.reactorId,
        //         actorDisplayName = row.reactorDisplayName,
        //         actorProfilePhotoUrl = row.reactorProfilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
        //         message = "${row.reactorDisplayName} reacted ${row.emoji} to your photo",
        //         createdAt = row.createdAt,
        //         photoUrl = r2StorageService.publicUrl(row.photoStorageKey),
        //     )
        // }

        val pending = friendshipRepository.findAllForUserWithStatus(userId, FriendshipStatus.PENDING)
        pending.filter { it.addressee.id == userId }.forEach { friendship ->
            events += ActivityEvent(
                type = ActivityEventType.REQUEST_INCOMING,
                actorId = friendship.requester.id,
                actorDisplayName = friendship.requester.displayName,
                actorProfilePhotoUrl = friendship.requester.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
                message = "${friendship.requester.displayName} wants to be friends",
                createdAt = friendship.createdAt,
            )
        }

        val accepted = friendshipRepository.findAllForUserWithStatus(userId, FriendshipStatus.ACCEPTED)

        // Only the original requester gets notified of an acceptance — the addressee already
        // knows, since accepting was their own action.
        accepted.filter { it.requester.id == userId && it.respondedAt != null }.forEach { friendship ->
            events += ActivityEvent(
                type = ActivityEventType.REQUEST_ACCEPTED,
                actorId = friendship.addressee.id,
                actorDisplayName = friendship.addressee.displayName,
                actorProfilePhotoUrl = friendship.addressee.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
                message = "${friendship.addressee.displayName} accepted your friend request",
                createdAt = friendship.respondedAt!!,
            )
        }

        // One query covering every accepted friend's exchange history instead of one query per
        // friend in the loop below.
        val acceptedFriendIds = accepted.map { if (it.requester.id == userId) it.addressee.id else it.requester.id }
        val timestampsByFriend = if (acceptedFriendIds.isEmpty()) {
            emptyMap()
        } else {
            photoRecipientRepository.findExchangeTimestampsBatch(userId, acceptedFriendIds)
                .groupBy({ it.otherPartyId }, { StreakExchange(it.createdAt, it.sentByMe) })
        }

        // One query covering every accepted friendship's break state, same batching reasoning as
        // the exchange-timestamps query above. Keyed by friendshipId, which is this repository's
        // own primary key (see FriendshipStreakStateRepository's own doc comment).
        val streakStateByFriendshipId = friendshipStreakStateRepository
            .findAllById(accepted.map { it.id })
            .associateBy { it.friendshipId }

        accepted.forEach { friendship ->
            val friend = if (friendship.requester.id == userId) friendship.addressee else friendship.requester
            val streakState = streakStateByFriendshipId[friendship.id]

            val exchanges = timestampsByFriend[friend.id] ?: emptyList()
            if (exchanges.isEmpty()) return@forEach

            // Both of these have to see restoredThroughDate for the same reason FriendService's
            // own copy of this computation does — a restored day is a real part of the chain, and
            // leaving it out here made this service disagree with the Friends list about both the
            // streak's value and whether it's currently at risk, for exactly the friendships
            // someone had just paid to restore.
            val streak = StreakCalculator.compute(exchanges, streakState?.restoredThroughDate)
            // The most recent day BOTH sides sent a photo — not just either direction — since
            // that's the same rule the streak count itself now uses. One person sending today
            // without a reply yet must not push this forward, or the "expiring" warning below
            // would never fire on the very day it's actually needed.
            val mostRecentDay = StreakCalculator.mostRecentMutualDay(exchanges, streakState?.restoredThroughDate)

            // A friendship's streak having actually broken (as opposed to "at risk" below, which
            // is about a still-live streak) is its own event, driven by StreakBreakDetectionService's
            // own record of the break rather than a live recompute here — that job is the one
            // place `brokenAt` gets set, and this just surfaces it the same way STREAK_EXPIRING
            // surfaces a live computation. Timestamped at the real break moment, not "now", for the
            // same reason STREAK_EXPIRING below anchors on the start of today rather than fetch time.
            // Three guards, because brokenAt alone outlives what it describes: restoredThroughDate
            // being set means this exact break was already paid for and undone; a currently
            // positive streak means the friendship has moved on regardless of what the last
            // recorded break says (the detection job clears brokenAt for that case too, but only
            // on its next run — this stays correct in between); and the restore deadline having
            // passed means there's nothing left to act on, so the row would just sit in the feed
            // indefinitely. Bounding on the deadline rather than a separate duration keeps this
            // row alive for exactly as long as the Friends tab offers its matching restore pill,
            // so the two can never disagree about whether a break is still actionable.
            val restoreStillOpen = streakState?.restoreDeadline?.isAfter(Instant.now()) == true
            if (streak == 0 && streakState?.restoredThroughDate == null && restoreStillOpen) {
                streakState?.brokenAt?.let { brokenAt ->
                    events += ActivityEvent(
                        type = ActivityEventType.STREAK_BROKEN,
                        actorId = friend.id,
                        actorDisplayName = friend.displayName,
                        actorProfilePhotoUrl = friend.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
                        message = "Your streak with ${friend.displayName} broke",
                        createdAt = brokenAt,
                        warn = true,
                    )
                }
            }

            // The same threshold the Friends list's own hourglass applies, via the one shared
            // helper that exists precisely so these two can't drift — this used to inline its own
            // "yesterday was mutual, today isn't yet" check with no threshold at all, which is
            // true for the whole day: Activity would announce a streak "expiring" more than 20
            // hours before anything was actually at stake, while the Friends tab stayed silent
            // until the last few hours.
            // Deadline via the shared helper rather than recomputing "tomorrow's UTC midnight" by
            // hand here — that inline copy was one more place the same rule could drift from
            // StreakCalculator's own version of it.
            val windowDeadline = StreakCalculator.currentWindowDeadline(streak, mostRecentDay)
            if (windowDeadline != null && StreakCalculator.isAtRisk(streak, mostRecentDay)) {
                val hoursLeft = Duration.between(Instant.now(), windowDeadline).toHours().coerceAtLeast(1)
                events += ActivityEvent(
                    type = ActivityEventType.STREAK_EXPIRING,
                    actorId = friend.id,
                    actorDisplayName = friend.displayName,
                    actorProfilePhotoUrl = friend.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
                    message = "Your streak with ${friend.displayName} expires in ${hoursLeft}h",
                    // Dated to when the warning itself became true — the moment the at-risk window
                    // opened. Not "now", which re-stamped on every fetch and left the relative
                    // time permanently reading "just now"; and no longer the start of the day
                    // either, which was roughly right back when this fired all day long but is
                    // badly wrong now that it only fires in the final hours: a warning that just
                    // appeared would sort below everything else that happened earlier that day,
                    // burying the most urgent row in the feed at exactly the point it matters.
                    createdAt = StreakCalculator.atRiskWindowOpensAt(windowDeadline),
                    warn = true,
                )
            }
        }

        return events.sortedByDescending { it.createdAt }
    }
}
