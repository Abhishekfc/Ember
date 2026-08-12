package com.emigo.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityEventType { PHOTO_RECEIVED, STREAK_EXPIRING, STREAK_BROKEN, REQUEST_ACCEPTED, REQUEST_INCOMING }

@Serializable
data class ActivityEventDto(
    val type: ActivityEventType,
    val actorId: String,
    val actorDisplayName: String,
    val actorProfilePhotoUrl: String? = null,
    val message: String,
    val createdAt: String,
    val warn: Boolean = false,
    val photoUrl: String? = null,
)

/** Backs the Activity tab's nav-dock badge dot — a real per-account value from the backend (not
 * on-device storage), so it survives a reinstall. `lastSeenAt` is null for an account that's
 * never actually viewed the Activity tab. */
@Serializable
data class ActivityLastSeenDto(val lastSeenAt: String?)
