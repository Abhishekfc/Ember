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
