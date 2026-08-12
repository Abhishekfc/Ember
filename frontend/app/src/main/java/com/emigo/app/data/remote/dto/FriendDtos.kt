package com.emigo.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class FriendSummaryDto(
    val friendshipId: String,
    val friendId: String,
    val displayName: String,
    val username: String,
    val email: String,
    val profilePhotoUrl: String?,
    val pinnedByMe: Boolean,
    val pinnedByThem: Boolean,
    val lastActivityAt: String?,
    // Null exactly when lastActivityAt is — true if the most recent exchange was this user
    // sending to the friend, false if the friend sent it to this user.
    val lastActivityBySelf: Boolean?,
    val streak: Int,
    // Deadlines (epoch seconds), not precomputed booleans — evaluated fresh against the device's
    // own clock every time FriendsScreen renders a row (see FriendRow's own STREAK_AT_RISK_
    // THRESHOLD_SECONDS check), rather than trusting whatever was true at the moment this
    // response was fetched. A boolean fixed at fetch time goes stale the moment the response is
    // read from LocalListCache later (offline, or just after time has passed) — "at risk" could
    // stay stuck false for hours after it became genuinely true, or a restore pill could keep
    // showing well past its real deadline, entirely disconnected from the actual current time.
    // Defaulted null (not just non-optional) so a locally cached copy of this DTO written before
    // this field existed still deserializes cleanly, same reasoning UserProfileDto.createdAt
    // already documents for its own added-later field.
    val streakDeadlineEpochSeconds: Long? = null,
    val streakRestoreDeadlineEpochSeconds: Long? = null,
)

@Serializable
data class PendingFriendRequestDto(
    val friendshipId: String,
    val requesterId: String,
    val displayName: String,
    val username: String,
    val email: String,
    val profilePhotoUrl: String? = null,
    val createdAt: String,
)

@Serializable
data class FriendSearchResultDto(
    val userId: String,
    val displayName: String,
    val username: String,
    val requested: Boolean,
    // Both null/false when there's no relationship at all — see the backend FriendSearchResult
    // DTO's own doc comment for the exact semantics (only true when *this* user is the one who
    // sent a still-pending request, which is the one case cancelable from this screen).
    val friendshipId: String? = null,
    val isPendingFromMe: Boolean = false,
    // True when the found user is the one who sent the still-pending request — this user has a
    // request waiting on them, offered as Accept/Decline on that person's profile page.
    val isPendingFromThem: Boolean = false,
)

@Serializable
data class FriendRequestBody(
    val targetUserId: String? = null,
    val email: String? = null,
)

@Serializable
data class FriendAcceptBody(
    val friendshipId: String,
)

/** A saved recipient-picker shortcut (see RecipientPickerViewModel) — kept on the backend, not
 * just on-device, so a list created on one phone shows up after logging into the same account on
 * another. */
@Serializable
data class RecipientListDto(
    val id: String,
    val name: String,
    val friendIds: List<String>,
    val createdAt: String,
)

@Serializable
data class CreateRecipientListBody(
    val name: String,
    val friendIds: List<String>,
)
