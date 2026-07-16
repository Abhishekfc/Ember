package com.ember.backend.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class FriendRequestRequest(
    @field:Email @field:NotBlank val email: String,
)

data class FriendAcceptRequest(
    @field:NotNull val friendshipId: UUID,
)

data class FriendSummary(
    val friendshipId: UUID,
    val friendId: UUID,
    val displayName: String,
    val email: String,
    val pinnedByMe: Boolean,
    val pinnedByThem: Boolean,
    val lastActivityAt: Instant?,
    val streak: Int,
)

data class PendingFriendRequest(
    val friendshipId: UUID,
    val requesterId: UUID,
    val displayName: String,
    val email: String,
    val createdAt: Instant,
)
