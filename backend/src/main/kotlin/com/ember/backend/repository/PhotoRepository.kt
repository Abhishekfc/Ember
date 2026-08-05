package com.ember.backend.repository

import com.ember.backend.model.Photo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface PhotoRepository : JpaRepository<Photo, UUID> {

    /** Every photo this user has *saved* within [start, end) — backs the Memories grid's
     * month-at-a-time browsing (the client computes one calendar month's local-time boundaries
     * and passes them as UTC instants), so a single request is naturally bounded to one month's
     * worth of photos without needing an arbitrary total-count cap. Sent-but-never-saved photos
     * deliberately don't show up here (see Photo.savedAt's own doc comment). */
    fun findBySenderIdAndSavedAtIsNotNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        senderId: UUID,
        start: Instant,
        end: Instant,
    ): List<Photo>

    /** Every photo this account has ever sent, unbounded — used only by account deletion, to
     * find every R2 object that needs deleting alongside the row itself. The `photos` row is
     * cleaned up for free by the DB's own cascade delete on `sender_id`; the actual file in R2
     * is not, since nothing in Postgres knows R2 exists. */
    fun findAllBySenderId(senderId: UUID): List<Photo>

    /** Unsaved photos no longer visible in *anyone's* feed — the same "superseded more than 24h
     * ago" grace-period rule PhotoRecipientRepository.findVisibleFeedPhotos uses to decide what a
     * single recipient still sees, generalized across every recipient a photo went to (a photo
     * with different recipient sets across different sends can genuinely expire at different
     * times for different people, so this only calls a photo eligible once it's expired for
     * *all* of them — the same "still needed by at least one person" bar the feed itself uses).
     * A photo sent to nobody at all (recipientless — not possible before this feature, but now
     * true for a captured-but-never-sent save-only photo, or a save that was never confirmed)
     * has no visibility rows at all and so is immediately eligible once unsaved.
     *
     * Deliberately excludes anything with a non-null saved_at at the SQL level — an explicit save
     * is permanent, full stop, never re-evaluated by this query regardless of feed visibility. */
    @Query(
        value = """
            with photo_visibility as (
                select
                    pr.photo_id,
                    lead(p.created_at) over (partition by p.sender_id, pr.recipient_id order by p.created_at asc) as next_created_at
                from photo_recipients pr
                join photos p on p.id = pr.photo_id
                where p.saved_at is null
            )
            select p.id
            from photos p
            where p.saved_at is null
            and not exists (
                select 1 from photo_visibility pv
                where pv.photo_id = p.id
                and (pv.next_created_at is null or pv.next_created_at >= :graceSince)
            )
        """,
        nativeQuery = true,
    )
    fun findExpiredUnsavedPhotoIds(@Param("graceSince") graceSince: Instant): List<UUID>

    /** This account's own unsaved sends from the last 24 hours — backs the Camera outbox list,
     * whose whole point is offering Unsend (see PhotoService.delete's own matching 24h gate) for
     * something just sent. A plain age bound, not the more permissive "still visible to a
     * recipient" rule the feed/cleanup job use — once a photo's past this window it's no longer
     * unsendable either, so there's no reason for it to keep appearing here. Saved photos are
     * permanent and belong to Memories instead (see Photo.savedAt) — excluded here so unsending
     * one can never also delete someone's own saved memory of it. */
    fun findBySenderIdAndSavedAtIsNullAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
        senderId: UUID,
        since: Instant,
    ): List<Photo>
}
