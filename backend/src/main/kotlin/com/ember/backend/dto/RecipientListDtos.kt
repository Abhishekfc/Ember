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
    @field:NotEmpty
    val friendIds: List<UUID>,
)
