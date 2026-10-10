package com.ember.backend.repository

import com.ember.backend.model.AdReward
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface AdRewardRepository : JpaRepository<AdReward, String> {

    /** Records a reward unless Google already reported this ad. Returns 1 when a row was added and
     * 0 when it was a repeat, decided by the database in one statement so two copies of the same
     * callback arriving together can't both succeed. */
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            insert into ad_rewards (transaction_id, user_id, friendship_id, created_at)
            values (:transactionId, :userId, :friendshipId, :now)
            on conflict (transaction_id) do nothing
        """,
    )
    fun insertIfAbsent(
        @Param("transactionId") transactionId: String,
        @Param("userId") userId: UUID,
        @Param("friendshipId") friendshipId: UUID,
        @Param("now") now: Instant,
    ): Int

    /** Ids of this user's unspent rewards for the friendship that are still fresh, oldest first. */
    @Query(
        """
        select r.transactionId from AdReward r
        where r.userId = :userId and r.friendshipId = :friendshipId
          and r.consumedAt is null and r.createdAt >= :since
        order by r.createdAt asc
        """,
    )
    fun findUnspentIds(
        @Param("userId") userId: UUID,
        @Param("friendshipId") friendshipId: UUID,
        @Param("since") since: Instant,
        pageable: Pageable,
    ): List<String>

    /** Spends one reward. Returns 1 only for the caller that actually spent it: the
     * `consumedAt is null` condition makes two simultaneous restores race for it in the database,
     * and exactly one wins. */
    @Modifying
    @Query("update AdReward r set r.consumedAt = :now where r.transactionId = :id and r.consumedAt is null")
    fun markSpent(@Param("id") id: String, @Param("now") now: Instant): Int

    @Modifying
    @Query("delete from AdReward r where r.createdAt < :before")
    fun deleteOlderThan(@Param("before") before: Instant): Int
}
