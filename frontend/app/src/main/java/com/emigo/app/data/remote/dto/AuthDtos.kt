package com.emigo.app.data.remote.dto

import kotlinx.serialization.Serializable

/** The one thing this app's own backend still needs after sign-up: a username and display name.
 * Everything about *who* this is — the email, proving it's really theirs, the password — is
 * Firebase Authentication's job now, so this carries no email or password at all. */
@Serializable
data class CompleteProfileRequestDto(
    val displayName: String,
    val username: String,
)
