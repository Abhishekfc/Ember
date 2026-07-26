package com.ember.app.data.remote.dto

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
    val streak: Int,
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
