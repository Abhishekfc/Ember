package com.ember.backend.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class ReportReason { SPAM, HARASSMENT, INAPPROPRIATE_CONTENT, FAKE_ACCOUNT, OTHER }

@Entity
@Table(name = "user_reports")
class UserReport(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    var reporter: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_user_id", nullable = false)
    var reportedUser: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var reason: ReportReason,

    @Column(length = 500)
    var details: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
