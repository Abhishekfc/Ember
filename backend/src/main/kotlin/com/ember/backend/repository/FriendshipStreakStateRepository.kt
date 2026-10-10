package com.ember.backend.repository

import com.ember.backend.model.FriendshipStreakState
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

/** Primary key is [FriendshipStreakState.friendshipId] itself (a one-to-one with `friendships`,
 * not a separate surrogate id) — `findById(friendshipId)` is already the exact per-friendship
 * lookup every caller needs, no extra derived-query method required for that. */
interface FriendshipStreakStateRepository : JpaRepository<FriendshipStreakState, UUID> {

    /** How many friendships have not been checked since [threshold]. One cheap count, used by the
     * start-up check to find out whether the nightly run already covered everything, without
     * loading every friendship (see StreakCatchUp.catchUpIsNeeded). Every check rewrites
     * `updatedAt`, so it doubles as "last checked at". */
    fun countByUpdatedAtBefore(threshold: Instant): Long
}
