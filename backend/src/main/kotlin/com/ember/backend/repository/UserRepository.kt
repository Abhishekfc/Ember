package com.ember.backend.repository

import com.ember.backend.model.User
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean

    // The lookup FirebaseAuthenticationFilter runs on every single authenticated request — how a
    // verified Firebase identity is matched back to its Emigo profile.
    fun findByFirebaseUid(firebaseUid: String): User?

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
