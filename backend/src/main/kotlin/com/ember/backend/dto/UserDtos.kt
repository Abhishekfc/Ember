package com.ember.backend.dto

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
    val username: String?,
)
