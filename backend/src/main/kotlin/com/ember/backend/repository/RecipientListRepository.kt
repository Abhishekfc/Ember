package com.ember.backend.repository

import com.ember.backend.model.RecipientList
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RecipientListRepository : JpaRepository<RecipientList, UUID> {
    fun findAllByOwnerIdOrderByCreatedAtAsc(ownerId: UUID): List<RecipientList>

    /** Backs the per-account list cap in RecipientListService — a count, not a fetch, since the
     * rows themselves aren't needed to decide whether another one is allowed. */
    fun countByOwnerId(ownerId: UUID): Long

    /** Scoped to [ownerId] in the query itself, not just checked after loading — so one account
     * can never delete another's list by guessing/reusing an id. Returns the number of rows
     * actually removed (0 if the id didn't exist or belonged to someone else), which the service
     * layer uses to decide whether to raise a not-found error. */
    fun deleteByIdAndOwnerId(id: UUID, ownerId: UUID): Long
}
