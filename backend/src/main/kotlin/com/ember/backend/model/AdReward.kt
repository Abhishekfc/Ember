package com.ember.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A rewarded ad that Google confirmed was watched, good for restoring one broken streak with
 * [friendshipId] (see [com.ember.backend.service.AdRewardService]). Only ever created from Google's
 * signed callback, never from anything the app sends. */
@Entity
@Table(name = "ad_rewards")
class AdReward(
    /** Google's id for the watched ad, so a retried callback can't record it twice. */
    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    val transactionId: String,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Column(name = "friendship_id", nullable = false, updatable = false)
    val friendshipId: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /** Null until a restore spends this reward. */
    @Column(name = "consumed_at")
    var consumedAt: Instant? = null,
)
