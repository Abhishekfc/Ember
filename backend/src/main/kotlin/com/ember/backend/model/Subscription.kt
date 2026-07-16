package com.ember.backend.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class SubscriptionStatus { NONE, ACTIVE, EXPIRED, CANCELLED }
enum class SubscriptionPlan { MONTHLY, YEARLY }

@Entity
@Table(name = "subscriptions")
class Subscription(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SubscriptionStatus = SubscriptionStatus.NONE,

    @Enumerated(EnumType.STRING)
    @Column(name = "plan")
    var plan: SubscriptionPlan? = null,

    @Column(name = "play_purchase_token")
    var playPurchaseToken: String? = null,

    @Column(name = "play_product_id")
    var playProductId: String? = null,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
