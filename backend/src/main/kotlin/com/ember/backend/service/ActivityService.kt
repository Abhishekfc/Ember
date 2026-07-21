package com.ember.backend.service

import com.ember.backend.dto.ActivityEvent
import com.ember.backend.dto.ActivityEventType
import com.ember.backend.model.FriendshipStatus
import com.ember.backend.repository.FriendshipRepository
import com.ember.backend.repository.PhotoRecipientRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private const val RECENT_PHOTOS_LIMIT = 20
private const val ACTIVITY_FEED_LIMIT = 30

@Service
class ActivityService(
    private val friendshipRepository: FriendshipRepository,
    private val photoRecipientRepository: PhotoRecipientRepository,
    private val r2StorageService: R2StorageService,
) {

    fun getActivity(userId: UUID): List<ActivityEvent> {
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

        accepted.forEach { friendship ->
            val friend = if (friendship.requester.id == userId) friendship.addressee else friendship.requester
            val timestamps = photoRecipientRepository.findExchangeTimestamps(userId, friend.id)
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

        return events.sortedByDescending { it.createdAt }.take(ACTIVITY_FEED_LIMIT)
    }
}
