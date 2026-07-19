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

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
