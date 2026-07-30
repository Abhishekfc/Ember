package com.ember.backend.repository

import com.ember.backend.model.BlockedUser
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlockedUserRepository : JpaRepository<BlockedUser, UUID> {

    fun existsByBlocker_IdAndBlocked_Id(blockerId: UUID, blockedId: UUID): Boolean

    fun findByBlocker_IdAndBlocked_Id(blockerId: UUID, blockedId: UUID): BlockedUser?

    fun findAllByBlocker_IdOrderByCreatedAtDesc(blockerId: UUID): List<BlockedUser>

    // Backs existsBetween below — a plain derived query (no @Query/JPQL needed at all) covering
    // both directions in one call, generated and guaranteed correct by Spring Data itself rather
    // than hand-written JPQL.
    fun existsByBlocker_IdAndBlocked_IdOrBlocker_IdAndBlocked_Id(
        blockerId1: UUID,
        blockedId1: UUID,
        blockerId2: UUID,
        blockedId2: UUID,
    ): Boolean
}

/** Whether either side has blocked the other — used everywhere two people could otherwise
 * interact (search, sending a friend request) to enforce mutual invisibility regardless of which
 * of them did the actual blocking.
 *
 * A plain extension function, not a method with a body inside the interface above — Kotlin
 * doesn't compile an interface method-with-a-body as a true JVM `default` method unless the
 * module opts into `-Xjvm-default`, which this project doesn't. Left as one, Spring Data still
 * sees it as an abstract method needing a derived query (this app doesn't set that compiler
 * flag) and crashes on startup trying to parse "existsBetween" as a query — a real
 * UnsatisfiedDependencyException hit standing this up. An extension function is a plain static
 * utility as far as the JVM/Spring is concerned, never a repository method Spring Data could
 * mistake for one. */
fun BlockedUserRepository.existsBetween(userA: UUID, userB: UUID): Boolean =
    existsByBlocker_IdAndBlocked_IdOrBlocker_IdAndBlocked_Id(userA, userB, userB, userA)
