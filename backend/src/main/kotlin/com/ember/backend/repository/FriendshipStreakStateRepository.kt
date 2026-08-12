package com.ember.backend.repository

import com.ember.backend.model.FriendshipStreakState
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Primary key is [FriendshipStreakState.friendshipId] itself (a one-to-one with `friendships`,
 * not a separate surrogate id) — `findById(friendshipId)` is already the exact per-friendship
 * lookup every caller needs, no extra derived-query method required for that. */
interface FriendshipStreakStateRepository : JpaRepository<FriendshipStreakState, UUID>
