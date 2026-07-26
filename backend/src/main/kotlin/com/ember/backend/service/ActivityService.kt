package com.ember.backend.service

import com.ember.backend.dto.ActivityEvent
import com.ember.backend.dto.ActivityEventType
import com.ember.backend.dto.ActivityLastSeen
import com.ember.backend.dto.Page
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.FriendshipStatus
import com.ember.backend.repository.FriendshipRepository
import com.ember.backend.repository.PhotoRecipientRepository
import com.ember.backend.repository.UserRepository
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private const val RECENT_PHOTOS_LIMIT = 20

@Service
class ActivityService(
    private val friendshipRepository: FriendshipRepository,
    private val photoRecipientRepository: PhotoRecipientRepository,
    private val userRepository: UserRepository,
    private val r2StorageService: R2StorageService,
    private val cacheManager: CacheManager,
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
                .groupBy({ it.otherPartyId }, { it.createdAt })
        }

        accepted.forEach { friendship ->
            val friend = if (friendship.requester.id == userId) friendship.addressee else friendship.requester
            val timestamps = timestampsByFriend[friend.id] ?: emptyList()
            if (timestamps.isEmpty()) return@forEach

            val streak = StreakCalculator.compute(timestamps)
            val mostRecentDay = timestamps.maxOf { it.atZone(ZoneOffset.UTC).toLocalDate() }
            val today = LocalDate.now(ZoneOffset.UTC)

            // Streak is only "at risk" once today hasn't been exchanged yet but yesterday was —
            // a streak with today already covered isn't expiring.
            if (streak > 0 && mostRecentDay == today.minusDays(1)) {
                val midnightUtc = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                val hoursLeft = Duration.between(Instant.now(), midnightUtc).toHours().coerceAtLeast(1)
                events += ActivityEvent(
                    type = ActivityEventType.STREAK_EXPIRING,
                    actorId = friend.id,
                    actorDisplayName = friend.displayName,
                    actorProfilePhotoUrl = friend.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
                    message = "Your streak with ${friend.displayName} expires in ${hoursLeft}h",
                    // The warning became true the moment today started without an exchange yet —
                    // not "now", which re-stamped on every single fetch/refresh and made the
                    // relative time permanently stuck reading "just now" no matter how long the
                    // streak had actually been at risk.
                    createdAt = today.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    warn = true,
                )
            }
        }

        return events.sortedByDescending { it.createdAt }
    }
}
