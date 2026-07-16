package com.ember.backend.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class FriendshipStatus { PENDING, ACCEPTED, DECLINED }

@Entity
@Table(name = "friendships")
class Friendship(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    var requester: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addressee_id", nullable = false)
    var addressee: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FriendshipStatus = FriendshipStatus.PENDING,

    @Column(name = "requester_pinned", nullable = false)
    var requesterPinned: Boolean = false,

    @Column(name = "addressee_pinned", nullable = false)
    var addresseePinned: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "responded_at")
    var respondedAt: Instant? = null,
)
