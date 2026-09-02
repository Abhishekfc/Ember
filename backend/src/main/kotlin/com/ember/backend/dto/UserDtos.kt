package com.ember.backend.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class UserProfile(
    val userId: UUID,
    val displayName: String,
    val username: String,
    val email: String,
    val profilePhotoUrl: String?,
    // Backs the Memories grid's "don't let 'previous month' go back further than this account
    // ever existed" limit (see HomeViewModel.canGoToPreviousMonth on the Android client).
    val createdAt: Instant,
    // The client checks this against Firebase's own (locally cached) isEmailVerified flag to
    // decide whether to show the "verify your email" screen — see FirebaseAuthenticationFilter
    // for the actual enforcement, this is just what lets the app show the right screen *before*
    // hitting a wall on some other endpoint.
    val emailVerificationRequired: Boolean,
)

data class UpdateProfileRequest(
    @field:Size(max = 100)
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

/** No suggestions counterpart to [UsernameAvailability] — an email address isn't something the
 * server can offer alternatives for the way it can for a taken username. */
data class EmailAvailability(
    val available: Boolean,
)

/** The one thing signing in by username needs that Firebase itself has no concept of: Firebase
 * Authentication signs in by email only, so a username typed into the login screen has to be
 * resolved back to its email here, server-side, before the client can hand it to Firebase at all.
 * [email] is null when [username] doesn't match any account — the client treats that exactly like
 * a wrong password (see LoginViewModel's own submitLogin), not a distinct "username not found",
 * for the same reason [EmailAvailability] doesn't reveal more than it has to during sign-up. */
data class UsernameLoginLookup(
    val email: String?,
)
