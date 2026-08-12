package com.ember.backend.repository

import com.ember.backend.model.Friendship
import com.ember.backend.model.FriendshipStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface FriendshipRepository : JpaRepository<Friendship, UUID> {

    /** Every friendship in the system with a given status, regardless of who's on either side —
     * used by the streak-break scheduled job, which has to check every accepted friendship once a
     * day, not just one user's own. `join fetch` on both sides is required, not just an N+1
     * nicety: the job reads `requester`/`addressee` display names after this query's own
     * transaction has closed, and those associations are lazy — without eager-fetching them here,
     * that read throws `LazyInitializationException` on a detached entity instead of just being
     * a wasted extra query. */
    @Query("select f from Friendship f join fetch f.requester join fetch f.addressee where f.status = :status")
    fun findAllByStatus(@Param("status") status: FriendshipStatus): List<Friendship>

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

    /** Batched form of [findBetween] for checking many recipients against one sender at once
     * (used by PhotoService.upload) — one query instead of one per recipient. */
    @Query(
        """
        select f from Friendship f
        where f.status = :status
        and ((f.requester.id = :userId and f.addressee.id in :otherIds)
          or (f.addressee.id = :userId and f.requester.id in :otherIds))
        """
    )
    fun findAllWithStatusBetween(
        @Param("userId") userId: UUID,
        @Param("otherIds") otherIds: Collection<UUID>,
        @Param("status") status: FriendshipStatus,
    ): List<Friendship>

    /** Same as [findAllWithStatusBetween] but regardless of status — used by
     * FriendService.searchUsers to flag every result that already has *any* relationship
     * (pending or accepted) with one query instead of one [findBetween] call per search result. */
    @Query(
        """
        select f from Friendship f
        where (f.requester.id = :userId and f.addressee.id in :otherIds)
           or (f.addressee.id = :userId and f.requester.id in :otherIds)
        """
    )
    fun findAllBetween(
        @Param("userId") userId: UUID,
        @Param("otherIds") otherIds: Collection<UUID>,
    ): List<Friendship>
}
