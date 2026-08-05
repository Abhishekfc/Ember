package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PhotoUploadResponseDto(
    val photoId: String,
    val url: String,
    val createdAt: String,
    val recipientIds: List<String>,
    val saved: Boolean,
)

@Serializable
data class AddPhotoRecipientsBody(
    val recipientIds: List<String>,
)
