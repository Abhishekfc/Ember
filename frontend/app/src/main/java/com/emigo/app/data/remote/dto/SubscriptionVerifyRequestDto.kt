package com.emigo.app.data.remote.dto

import kotlinx.serialization.Serializable

/** Body of `POST /subscription/verify` — the purchase token Play handed back plus the product it
 * was for. The backend verifies both against Google server-to-server before granting Gold; the
 * field names match the backend's `SubscriptionVerifyRequest` exactly. */
@Serializable
data class SubscriptionVerifyRequestDto(
    val purchaseToken: String,
    val productId: String,
)
