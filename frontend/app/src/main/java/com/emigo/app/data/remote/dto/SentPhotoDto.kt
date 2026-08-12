package com.emigo.app.data.remote.dto

import kotlinx.serialization.Serializable

/** One entry in the Camera outbox — this account's own recent, unsaved sends, still within
 * their unsend window (see PhotoRepository.getSentPhotos/unsendPhoto). */
@Serializable
data class SentPhotoDto(
    val photoId: String,
    val photoUrl: String,
    val createdAt: String,
)
