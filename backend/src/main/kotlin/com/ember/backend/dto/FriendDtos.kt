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
    // Null exactly when lastActivityAt is — true if the most recent exchange was this user
    // sending to the friend, false if the friend sent it to this user.
    val lastActivityBySelf: Boolean?,
    val streak: Int,
)

data class PendingFriendRequest(
    val friendshipId: UUID,
    val requesterId: UUID,
    val displayName: String,
    val username: String,
    val email: String,
    val profilePhotoUrl: String?,
    val createdAt: Instant,
)

data class FriendSearchResult(
    val userId: UUID,
    val displayName: String,
    val username: String,
    val requested: Boolean,
    // Both null when there's no relationship at all. When a friendship (of any status) exists,
    // friendshipId is the existing DELETE /friends/{friendshipId} endpoint's own key — reused
    // as-is for canceling a still-pending request, not a new capability the backend needed.
    // isPendingFromMe is what actually gates whether the client offers a cancel action at all:
    // true only when it's PENDING *and* the searching user is the one who sent it — a request
    // someone else sent you, or an already-accepted friendship, both still set `requested = true`
    // for display, but aren't this user's to cancel.
    val friendshipId: UUID? = null,
    val isPendingFromMe: Boolean = false,
    // The mirror of isPendingFromMe: true only when it's PENDING *and* the found user is the one
    // who sent it — this user has a request waiting on them, which the profile page reached from
    // this search result can accept or decline. False for an already-accepted friendship, which
    // still sets `requested = true` but offers no action here.
    val isPendingFromThem: Boolean = false,
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
