package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityEventType { PHOTO_RECEIVED, STREAK_EXPIRING, REQUEST_ACCEPTED, REQUEST_INCOMING }

@Serializable
data class ActivityEventDto(
    val type: ActivityEventType,
    val actorId: String,
    val actorDisplayName: String,
    val message: String,
    val createdAt: String,
    val warn: Boolean = false,
)
