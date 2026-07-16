package com.ember.backend.dto

import com.ember.backend.model.SubscriptionPlan
import com.ember.backend.model.SubscriptionStatus
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class SubscriptionStatusResponse(
    val status: SubscriptionStatus,
    val plan: SubscriptionPlan?,
    val expiresAt: Instant?,
)

data class SubscriptionVerifyRequest(
    @field:NotBlank val purchaseToken: String,
    @field:NotBlank val productId: String,
)
