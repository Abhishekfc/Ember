package com.ember.backend.dto

import java.time.Instant
import java.util.UUID

enum class ActivityEventType { PHOTO_RECEIVED, STREAK_EXPIRING, REQUEST_ACCEPTED, REQUEST_INCOMING }

data class ActivityEvent(
    val type: ActivityEventType,
    val actorId: UUID,
    val actorDisplayName: String,
    val message: String,
    val createdAt: Instant,
    val warn: Boolean = false,
)
