package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class BlockedUserDto(
    val userId: String,
    val displayName: String,
    val username: String,
    val profilePhotoUrl: String?,
    val blockedAt: String,
)

/** Names match the backend's ReportReason enum exactly (kotlinx.serialization and Jackson both
 * serialize/deserialize enums by their declared name by default) — a single source of truth on
 * each side rather than a free-form string either could drift out of sync on. */
@Serializable
enum class ReportReason { SPAM, HARASSMENT, INAPPROPRIATE_CONTENT, FAKE_ACCOUNT, OTHER }

@Serializable
data class ReportUserRequestDto(
    val reason: ReportReason,
    val details: String? = null,
)
