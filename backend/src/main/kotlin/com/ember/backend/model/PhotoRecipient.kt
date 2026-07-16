package com.ember.backend.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "photo_recipients")
class PhotoRecipient(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photo_id", nullable = false)
    var photo: Photo,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    var recipient: User,

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,

    @Column(name = "viewed_at")
    var viewedAt: Instant? = null,
)
