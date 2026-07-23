package com.ember.backend.repository

import com.ember.backend.model.Subscription
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SubscriptionRepository : JpaRepository<Subscription, UUID> {
    fun findByUserId(userId: UUID): Subscription?
    fun findByPlayPurchaseToken(playPurchaseToken: String): Subscription?
}
