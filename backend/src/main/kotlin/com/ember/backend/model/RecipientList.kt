package com.ember.backend.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "recipient_lists")
class RecipientList(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,

    @Column(nullable = false, length = 60)
    var name: String,

    // JSON-encoded array of friend user IDs — see this table's own migration comment for why a
    // join table wasn't used: always read/written as one whole unit, never a partial-member
    // operation.
    @Column(name = "friend_ids", nullable = false, columnDefinition = "TEXT")
    var friendIdsJson: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
