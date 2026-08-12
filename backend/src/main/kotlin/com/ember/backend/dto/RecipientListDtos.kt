package com.ember.backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class RecipientListSummary(
    val id: UUID,
    val name: String,
    val friendIds: List<UUID>,
    val createdAt: Instant,
)

data class CreateRecipientListRequest(
    @field:NotBlank @field:Size(max = 60)
    val name: String,
    // Bounded, not just non-empty: this list is fed straight into a `... id in :ids` query, so an
    // unbounded one let a single request build an arbitrarily large IN clause. 200 is well past
    // any real friend list, and the service narrows it to actual friends regardless.
    @field:NotEmpty @field:Size(max = 200)
    val friendIds: List<UUID>,
)
