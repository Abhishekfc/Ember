package com.ember.backend.service

import com.ember.backend.dto.SubscriptionStatusResponse
import com.ember.backend.exception.PurchaseTokenAlreadyClaimedException
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

    /** A subscription past its own [Subscription.expiresAt] is treated as expired here even if
     * nothing has re-verified it since — without this, a cancelled/lapsed subscription keeps
     * reporting ACTIVE indefinitely, since nothing re-checks Google unless the client happens to
     * call [verify] again. */
    fun getStatus(userId: UUID): SubscriptionStatusResponse {
        val subscription = subscriptionRepository.findByUserId(userId)
        val expiresAt = subscription?.expiresAt
        val isStale = subscription?.status == SubscriptionStatus.ACTIVE && expiresAt != null && expiresAt.isBefore(Instant.now())
        return SubscriptionStatusResponse(
            status = if (isStale) SubscriptionStatus.EXPIRED else (subscription?.status ?: SubscriptionStatus.NONE),
            plan = subscription?.plan,
            expiresAt = expiresAt,
        )
    }

    /** Verifies the purchase against Google (real server-to-server check, not trusting the
     * client's own claim) and then, separately, checks that this exact purchase token isn't
     * *already* attached to a different account — Google only confirms the token/product pair is
     * genuine, it doesn't know or care which of our accounts is asking. Without this second
     * check, one real purchase's token could be shared/replayed by any number of other accounts
     * to each grant themselves a free subscription. */
    @Transactional
    fun verify(userId: UUID, productId: String, purchaseToken: String): SubscriptionStatusResponse {
        val result = playBillingVerificationService.verifySubscription(productId, purchaseToken)

        val existingClaim = subscriptionRepository.findByPlayPurchaseToken(purchaseToken)
        if (existingClaim != null && existingClaim.user.id != userId) {
            throw PurchaseTokenAlreadyClaimedException()
        }

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

    /** The one server-side source of truth for "is this user actually a paying Gold member" —
     * gated actions (streak restore, and any future one) must check this themselves rather than
     * trusting a client-supplied flag; the client's own `isGoldMember` is only ever a cached
     * mirror of what this same query already computes. Re-derives [getStatus] rather than reading
     * the subscription row directly, so the staleness/expiry handling there (a lapsed
     * subscription past its own expiresAt reads as EXPIRED even before Google's been re-checked)
     * only has to live in one place. */
    fun isActiveGoldMember(userId: UUID): Boolean = getStatus(userId).status == SubscriptionStatus.ACTIVE

    private fun derivePlan(productId: String): SubscriptionPlan =
        if (productId.contains("year", ignoreCase = true)) SubscriptionPlan.YEARLY else SubscriptionPlan.MONTHLY
}
