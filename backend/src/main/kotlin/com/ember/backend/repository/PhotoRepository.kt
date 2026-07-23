package com.ember.backend.repository

import com.ember.backend.model.Photo
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface PhotoRepository : JpaRepository<Photo, UUID> {

    /** Every photo this user has sent within [start, end) — backs the Memories grid's
     * month-at-a-time browsing (the client computes one calendar month's local-time boundaries
     * and passes them as UTC instants), so a single request is naturally bounded to one month's
     * worth of photos without needing an arbitrary total-count cap. */
    fun findBySenderIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        senderId: UUID,
        start: Instant,
        end: Instant,
    ): List<Photo>
}
