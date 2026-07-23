package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    val userId: String,
    val displayName: String,
    val username: String,
    val email: String,
    val profilePhotoUrl: String?,
    // Nullable with a default (rather than a plain non-null String) so a profile object cached
    // by an older app version — before this field existed — still deserializes cleanly instead
    // of failing outright; `ignoreUnknownKeys` on LocalListCache's Json only covers *extra*
    // fields, not ones the class expects that are missing from old cached JSON.
    val createdAt: String? = null,
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
