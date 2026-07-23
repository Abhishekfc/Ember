package com.ember.backend.dto

import java.time.Instant
import java.util.UUID

data class PhotoUploadResponse(
    val photoId: UUID,
    val url: String,
    val createdAt: Instant,
    val recipientIds: List<UUID>,
)

/** [seen] is scoped to the single (photo, recipient) pair this entry represents — when a photo
 * is sent to several people, each of their own feeds reports their own independent seen state
 * for it, never a shared/combined one. */
data class PhotoEntry(
    val photoId: UUID,
    val photoUrl: String,
    val createdAt: Instant,
    val seen: Boolean,
)

data class FeedItem(
    val friendId: UUID,
    val displayName: String,
    val photos: List<PhotoEntry>,
    val streak: Int,
)

/** One photo in the Memories grid — always one this user sent themselves, with no time-window
 * cutoff (unlike the 24h Home feed). */
data class MemoryPhoto(
    val photoId: UUID,
    val photoUrl: String,
    val createdAt: Instant,
)
