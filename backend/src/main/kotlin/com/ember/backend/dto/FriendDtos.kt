package com.ember.backend.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class FriendRequestRequest(
    @field:Email @field:Size(max = 255)
    val email: String? = null,
    val targetUserId: UUID? = null,
)

data class FriendAcceptRequest(
    @field:NotNull val friendshipId: UUID,
)

data class FriendSummary(
    val friendshipId: UUID,
    val friendId: UUID,
    val displayName: String,
    val username: String,
    val email: String,
    val profilePhotoUrl: String?,
    val pinnedByMe: Boolean,
    val pinnedByThem: Boolean,
    val lastActivityAt: Instant?,
    val streak: Int,
)

data class PendingFriendRequest(
    val friendshipId: UUID,
    val requesterId: UUID,
    val displayName: String,
    val username: String,
    val email: String,
    val createdAt: Instant,
)

data class FriendSearchResult(
    val userId: UUID,
    val displayName: String,
    val username: String,
    val requested: Boolean,
)

/** A single page of an offset-paginated list — used for Friends, Activity, and Memories, the
 * three lists that load everything a user has ever accumulated (as opposed to the Home feed's
 * own 24h window, which is naturally small already). [hasMore] tells the client whether it's
 * worth requesting the next [offset] at all, rather than inferring it from [items].size, which
 * breaks the moment [limit] happens to evenly divide the total count. */
data class Page<T>(
    val items: List<T>,
    val hasMore: Boolean,
)
