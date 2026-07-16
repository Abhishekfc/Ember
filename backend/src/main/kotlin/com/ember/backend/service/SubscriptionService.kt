package com.ember.backend.service

import com.ember.backend.dto.SubscriptionStatusResponse
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.Subscription
import com.ember.backend.model.SubscriptionPlan
import com.ember.backend.model.SubscriptionStatus
import com.ember.backend.repository.SubscriptionRepository
import com.ember.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val playBillingVerificationService: PlayBillingVerificationService,
) {

    fun getStatus(userId: UUID): SubscriptionStatusResponse {
        val subscription = subscriptionRepository.findByUserId(userId)
        return SubscriptionStatusResponse(
            status = subscription?.status ?: SubscriptionStatus.NONE,
            plan = subscription?.plan,
            expiresAt = subscription?.expiresAt,
        )
    }

    @Transactional
    fun verify(userId: UUID, productId: String, purchaseToken: String): SubscriptionStatusResponse {
        val result = playBillingVerificationService.verifySubscription(productId, purchaseToken)

        val subscription = subscriptionRepository.findByUserId(userId) ?: Subscription(
            user = userRepository.findById(userId).orElseThrow { ResourceNotFoundException("User not found") }
        )

        subscription.status = if (result.isActive) SubscriptionStatus.ACTIVE else SubscriptionStatus.EXPIRED
        subscription.plan = derivePlan(productId)
        subscription.playPurchaseToken = purchaseToken
        subscription.playProductId = productId
        subscription.expiresAt = result.expiresAt
        subscription.updatedAt = Instant.now()
        subscriptionRepository.save(subscription)

        return SubscriptionStatusResponse(
            status = subscription.status,
            plan = subscription.plan,
            expiresAt = subscription.expiresAt,
        )
    }

    private fun derivePlan(productId: String): SubscriptionPlan =
        if (productId.contains("year", ignoreCase = true)) SubscriptionPlan.YEARLY else SubscriptionPlan.MONTHLY
}
