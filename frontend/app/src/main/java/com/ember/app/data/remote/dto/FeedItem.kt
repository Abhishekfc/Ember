package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PhotoEntryDto(
    val photoId: String,
    val photoUrl: String,
    val createdAt: String,
)

@Serializable
data class FeedItem(
    val friendId: String,
    val displayName: String,
    val photos: List<PhotoEntryDto>,
    val streak: Int,
)
