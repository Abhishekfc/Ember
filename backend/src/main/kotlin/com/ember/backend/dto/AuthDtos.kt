package com.ember.backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * The one step this backend still owns in the sign-up flow: choosing a username and display name.
 * Everything else — the email, proving it's really theirs, the password itself — is Firebase
 * Authentication's job now (see FirebaseTokenVerifier). This request carries no email at all;
 * AuthController reads it from the already-verified Firebase token instead, so there's no way to
 * submit an email other than the one actually behind that identity.
 *
 * Same constraints [UpdateProfileRequest.username] already enforces post-signup — the two must
 * agree, since a username rejected here would otherwise be silently accepted the moment someone
 * changed it later from Settings.
 */
data class CompleteProfileRequest(
    @field:NotBlank @field:Size(max = 100) val displayName: String,
    @field:NotBlank @field:Size(min = 3, max = 30)
    @field:Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "Username can only contain letters, numbers, underscores, and periods")
    val username: String,
)
