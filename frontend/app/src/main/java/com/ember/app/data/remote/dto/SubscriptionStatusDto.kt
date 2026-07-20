package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionStatusDto(
    val status: String,
    val plan: String?,
    val expiresAt: String?,
) {
    val isActive: Boolean get() = status == "ACTIVE"
}
