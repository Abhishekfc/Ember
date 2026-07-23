package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

/** [seen] is this signed-in user's own view state for this one photo — when a friend sends the
 * same photo to several people, each of their feeds reports its own independent seen state for
 * it (the backend tracks this per photo *and* recipient), never a shared one. */
@Serializable
data class PhotoEntryDto(
    val photoId: String,
    val photoUrl: String,
    val createdAt: String,
    val seen: Boolean,
)

@Serializable
data class FeedItem(
    val friendId: String,
    val displayName: String,
    val photos: List<PhotoEntryDto>,
    val streak: Int,
)

@Serializable
data class MemoryPhotoDto(
    val photoId: String,
    val photoUrl: String,
    val createdAt: String,
)
