package com.ember.backend.dto

import com.ember.backend.model.ReportReason
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class ReportUserRequest(
    val reason: ReportReason,
    // Bounded at the edge rather than only being truncated to 500 in ReportService: without this
    // a report could carry a multi-megabyte body that gets fully parsed and held in memory before
    // anything trims it. `details` maps to a `varchar(500)` column.
    @field:Size(max = 500)
    val details: String? = null,
)

data class BlockedUserSummary(
    val userId: UUID,
    val displayName: String,
    val username: String,
    val profilePhotoUrl: String?,
    val blockedAt: Instant,
)
