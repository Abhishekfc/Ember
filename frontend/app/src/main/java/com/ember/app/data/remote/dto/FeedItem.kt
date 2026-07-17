package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedItem(
    val friendId: String,
    val displayName: String,
    val photoUrl: String,
    val createdAt: String,
    val streak: Int,
)
