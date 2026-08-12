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
    // A deadline instant, not a precomputed boolean — the client evaluates both of these against
    // its own current time on every render, not just once when this response was fetched. A
    // fixed boolean was correct only in the instant it was computed; anyone looking at cached or
    // offline data (LocalListCache, no network) would keep seeing whatever was true at the last
    // successful fetch, drifting further wrong the longer they'd been offline — "at risk" could
    // stay stuck false for hours after it became genuinely true, or a restore pill could keep
    // showing well past its real deadline. Non-null exactly when there's a live streak whose
    // mutual-exchange window for today hasn't closed yet (yesterday was mutual, today isn't) —
    // this is when today's window actually closes (UTC midnight), not when the "close to the
    // deadline" threshold starts; the client owns deciding how close counts as "at risk" (see
    // FriendsScreen.STREAK_AT_RISK_THRESHOLD_SECONDS).
    val streakDeadlineEpochSeconds: Long?,
    // Non-null exactly while a real, unexpired restore window is open for this friendship (see
    // FriendshipStreakState.restoreDeadline) — same "client evaluates against its own clock"
    // reasoning as streakDeadlineEpochSeconds above.
    val streakRestoreDeadlineEpochSeconds: Long?,
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
