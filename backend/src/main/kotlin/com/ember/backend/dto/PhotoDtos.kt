package com.ember.backend.dto

import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class PhotoUploadResponse(
    val photoId: UUID,
    val url: String,
    val createdAt: Instant,
    val recipientIds: List<UUID>,
    val saved: Boolean,
)

data class AddPhotoRecipientsRequest(
    // Bounded at the edge as well as in PhotoService's own MAX_RECIPIENTS_PER_PHOTO check — the
    // service check happens *after* `distinct()` has already had to process the whole list, and
    // this endpoint was the one recipient-taking request with no `@Valid` at all.
    @field:Size(max = 200)
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

// Reaction feature disabled — see PhotoReactionService's own comment.
// data class SetReactionRequest(val emoji: String)
// data class SetReactionResponse(val myReaction: String?)

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

/** One entry in the Camera outbox — this account's own recent, unsaved sends, still within
 * their unsend window (see PhotoService.getRecentSent/delete). */
data class SentPhoto(
    val photoId: UUID,
    val photoUrl: String,
    val createdAt: Instant,
)
