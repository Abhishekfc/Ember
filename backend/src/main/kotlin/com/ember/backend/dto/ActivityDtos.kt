package com.ember.backend.dto

import java.time.Instant
import java.util.UUID

enum class ActivityEventType { PHOTO_RECEIVED, STREAK_EXPIRING, REQUEST_ACCEPTED, REQUEST_INCOMING }

data class ActivityEvent(
    val type: ActivityEventType,
    val actorId: UUID,
    val actorDisplayName: String,
    val actorProfilePhotoUrl: String? = null,
    val message: String,
    val createdAt: Instant,
    val warn: Boolean = false,
    /** Only set for PHOTO_RECEIVED — the most recent photo in the group, so the row shows what
     * was actually sent rather than just naming that something was. */
    val photoUrl: String? = null,
)
