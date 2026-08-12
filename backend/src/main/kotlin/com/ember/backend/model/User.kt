package com.ember.backend.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(nullable = false, unique = true)
    var username: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "display_name", nullable = false)
    var displayName: String,

    @Column(name = "profile_photo_storage_key")
    var profilePhotoStorageKey: String? = null,

    // Backs the Activity tab's nav-dock badge dot — see V5 migration's own comment for why this
    // is a real column rather than on-device storage.
    @Column(name = "activity_last_seen_at")
    var activityLastSeenAt: Instant? = null,

    // Any access token issued before this instant is rejected — see the V11 migration for the
    // full reasoning, and JwtAuthenticationFilter for where it's enforced. Null means no cutoff.
    @Column(name = "tokens_valid_from")
    var tokensValidFrom: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
