package com.ember.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One row per friendship, tracking just enough state to detect a streak *breaking* (streak
 * itself stays purely derived — see [com.ember.backend.service.StreakCalculator] — so this is
 * never the source of truth for "what is the streak right now", only "what did we last see it
 * as, and is a restore currently on offer". */
@Entity
@Table(name = "friendship_streak_states")
class FriendshipStreakState(
    @Id
    @Column(name = "friendship_id", nullable = false)
    val friendshipId: UUID,

    @Column(name = "last_known_streak", nullable = false)
    var lastKnownStreak: Int = 0,

    @Column(name = "broken_at")
    var brokenAt: Instant? = null,

    @Column(name = "restore_deadline")
    var restoreDeadline: Instant? = null,

    @Column(name = "restored_through_date")
    var restoredThroughDate: LocalDate? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
