package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    val userId: String,
    val displayName: String,
    val username: String,
    val email: String,
    val profilePhotoUrl: String?,
)

@Serializable
data class UpdateProfileRequestDto(
    val displayName: String? = null,
    val username: String? = null,
)

@Serializable
data class UsernameAvailabilityDto(
    val available: Boolean,
    val suggestions: List<String> = emptyList(),
)
