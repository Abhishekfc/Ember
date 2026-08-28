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

    // Null once an account has been migrated to (or created directly through) Firebase Auth —
    // see the V12 migration. Kept, not deleted, for accounts not yet migrated.
    @Column(name = "password_hash")
    var passwordHash: String? = null,

    // The uid Firebase Authentication assigned this identity — how an incoming, already-verified
    // Firebase token is matched back to this row (see FirebaseAuthenticationFilter). Null only
    // for the brief window during the one-time migration before an existing account has been
    // imported into Firebase.
    @Column(name = "firebase_uid", unique = true)
    var firebaseUid: String? = null,

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
