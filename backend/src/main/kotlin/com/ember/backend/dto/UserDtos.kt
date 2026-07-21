package com.ember.backend.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class UserProfile(
    val userId: UUID,
    val displayName: String,
    val username: String,
    val email: String,
    val profilePhotoUrl: String?,
)

data class UpdateProfileRequest(
    val displayName: String?,
    // Same constraints as RegisterRequest.username — previously unvalidated here, meaning a
    // profile update could set a username registration itself would have rejected.
    @field:Size(min = 3, max = 30)
    @field:Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "Username can only contain letters, numbers, underscores, and periods")
    val username: String?,
)

data class UsernameAvailability(
    val available: Boolean,
    val suggestions: List<String> = emptyList(),
)
