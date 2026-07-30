package com.ember.backend.dto

import com.ember.backend.model.ReportReason
import java.time.Instant
import java.util.UUID

data class ReportUserRequest(
    val reason: ReportReason,
    val details: String? = null,
)

data class BlockedUserSummary(
    val userId: UUID,
    val displayName: String,
    val username: String,
    val profilePhotoUrl: String?,
    val blockedAt: Instant,
)
