package com.ember.backend.repository

import com.ember.backend.model.Friendship
import com.ember.backend.model.FriendshipStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface FriendshipRepository : JpaRepository<Friendship, UUID> {

    @Query(
        """
        select f from Friendship f
        where (f.requester.id = :userId or f.addressee.id = :userId)
        and f.status = :status
        """
    )
    fun findAllForUserWithStatus(
        @Param("userId") userId: UUID,
        @Param("status") status: FriendshipStatus,
    ): List<Friendship>

    @Query(
        """
        select f from Friendship f
        where ((f.requester.id = :userId1 and f.addressee.id = :userId2)
            or (f.requester.id = :userId2 and f.addressee.id = :userId1))
        """
    )
    fun findBetween(
        @Param("userId1") userId1: UUID,
        @Param("userId2") userId2: UUID,
    ): Friendship?
}
