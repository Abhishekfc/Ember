package com.emigo.app.data.remote.dto

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
    // Same nullable-with-default reasoning as createdAt above — a profile cached before this
    // field existed shouldn't retroactively lock a returning user out of the app it already had
    // signed in; false ("not required") is the safe fallback for stale cached data specifically,
    // separate from what a fresh network response for a real account actually says.
    val emailVerificationRequired: Boolean = false,
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

@Serializable
data class EmailAvailabilityDto(
    val available: Boolean,
)

/** Firebase signs in by email only, no concept of a username at all — so a username typed into
 * the login screen has to be resolved back to its email here before it can be handed to Firebase.
 * A null [email] means no account has that username, and is treated exactly like a wrong password
 * by the caller (see LoginViewModel.submitLogin), not a distinct "username not found" message —
 * same reasoning [EmailAvailabilityDto] already follows for not revealing more than it has to. */
@Serializable
data class UsernameLoginLookupDto(
    val email: String?,
)
