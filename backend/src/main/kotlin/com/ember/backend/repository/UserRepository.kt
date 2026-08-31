package com.ember.backend.repository

import com.ember.backend.model.User
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean

    // The lookup FirebaseAuthenticationFilter runs on every single authenticated request — how a
    // verified Firebase identity is matched back to its Emigo profile.
    fun findByFirebaseUid(firebaseUid: String): User?

    // Candidates for EmailVerificationExpiryService's own cleanup: accounts still flagged as
    // pending verification and now past the grace period.
    //
    // Still not the final say on whether one actually gets deleted — this column records that
    // verification was *required*, while whether it has since *happened* is Firebase's answer, not
    // ours (see FirebaseTokenVerifier) — so every row returned here gets a live Firebase check
    // first. What the column does guarantee is that this query stays small: both that check and
    // FirebaseAuthenticationFilter clear the flag the moment an account is confirmed verified, so
    // what comes back is bounded by accounts genuinely still waiting, never by how many accounts
    // exist in total.
    fun findByEmailVerificationRequiredTrueAndCreatedAtBefore(cutoff: Instant): List<User>

    /**
     * Clears the pending-verification flag once verification is confirmed.
     *
     * A targeted UPDATE of that one column rather than `save(user)`, which would rewrite every
     * column of a detached entity from whatever values it was loaded with — on the authentication
     * path, where a concurrent request writing some other column (activityLastSeenAt being the
     * obvious one) would then be silently overwritten with the stale value this request happened
     * to read. One column is the only thing that should change here.
     *
     * `@Transactional` is mandatory on a `@Modifying` query, not decoration: Hibernate throws
     * TransactionRequiredException executing one outside a transaction, and the caller here is a
     * servlet filter with no transaction of its own. That exact mistake already shipped once in
     * this codebase — see PhotoService.markSeen, where it silently 500'd every mark-seen call.
     */
    @Modifying
    @Transactional
    @Query("update User u set u.emailVerificationRequired = false where u.id = :id")
    fun clearEmailVerificationRequired(@Param("id") id: UUID)

    // `:query` must already have its LIKE metacharacters escaped by the caller (see
    // FriendService.escapeLikeWildcards) — `escape '!'` below is what makes those escapes take
    // effect. Without it, a search for `%` matched every account in the system.
    //
    // Excludes either direction of a block (see BlockedUserRepository.existsBetween's own doc
    // comment for why mutual, not one-directional) — a blocked user must not be findable by, or
    // able to find, the person who blocked them. This is the only place that check needs to
    // live: FriendProfileViewModel never fetches a specific user's profile on its own, it only
    // ever reuses data from search/friends/pending list responses, so nobody reachable through
    // any of those three has a way to view a profile they shouldn't.
    @Query(
        """
        select u from User u
        where u.id <> :selfId
        and (lower(u.username) like lower(concat('%', :query, '%')) escape '!'
             or lower(u.displayName) like lower(concat('%', :query, '%')) escape '!')
        and not exists (
            select 1 from BlockedUser b
            where (b.blocker.id = :selfId and b.blocked.id = u.id)
               or (b.blocker.id = u.id and b.blocked.id = :selfId)
        )
        order by u.displayName asc
        """
    )
    fun search(
        @Param("selfId") selfId: UUID,
        @Param("query") query: String,
        pageable: Pageable,
    ): List<User>
}
