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

    @Query(
        """
        select u from User u
        where u.id <> :selfId
        and (lower(u.username) like lower(concat('%', :query, '%'))
             or lower(u.displayName) like lower(concat('%', :query, '%')))
        order by u.displayName asc
        """
    )
    fun search(
        @Param("selfId") selfId: UUID,
        @Param("query") query: String,
        pageable: Pageable,
    ): List<User>
}
