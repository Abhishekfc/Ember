package com.ember.backend.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "photos")
class Photo(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    var sender: User,

    @Column(name = "storage_key", nullable = false)
    var storageKey: String,

    @Column(name = "content_type", nullable = false)
    var contentType: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    // Null means "not saved" — a temporary photo, only kept around until it's no longer visible
    // in anyone's feed (see PhotoCleanupService). Non-null means the sender explicitly saved it
    // (the camera's bookmark button, or a pre-existing photo grandfathered in by migration V9),
    // and it's kept in storage indefinitely and shown in Memories.
    @Column(name = "saved_at")
    var savedAt: Instant? = null,
)
