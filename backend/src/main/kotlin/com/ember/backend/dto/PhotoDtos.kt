package com.ember.backend.dto

import java.time.Instant
import java.util.UUID

data class PhotoUploadResponse(
    val photoId: UUID,
    val url: String,
    val createdAt: Instant,
    val recipientIds: List<UUID>,
)

data class PhotoEntry(
    val photoId: UUID,
    val photoUrl: String,
    val createdAt: Instant,
)

data class FeedItem(
    val friendId: UUID,
    val displayName: String,
    val photos: List<PhotoEntry>,
    val streak: Int,
)
