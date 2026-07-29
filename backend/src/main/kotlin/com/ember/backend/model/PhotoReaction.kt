package com.ember.backend.model

// Whole reaction feature disabled — see PhotoReactionService's own comment for why (frontend was
// reverted after bugs, kept here commented rather than deleted in case it's revisited later).
// The `photo_reactions` table itself (V6__add_photo_reactions.sql) is deliberately left in place
// rather than also rolled back — an unused empty table is harmless, whereas editing a migration
// that may already be applied risks a Flyway checksum mismatch against the actual database.

/*
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "photo_reactions")
class PhotoReaction(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photo_id", nullable = false)
    var photo: Photo,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reactor_id", nullable = false)
    var reactor: User,

    @Column(nullable = false)
    var emoji: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
*/
