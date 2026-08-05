package com.ember.backend.repository

import com.ember.backend.model.PhotoRecipient
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface FeedRow {
    val photoId: UUID
    val senderId: UUID
    val senderDisplayName: String
    val senderProfilePhotoStorageKey: String?
    val storageKey: String
    val contentType: String
    val createdAt: Instant
    val viewedAt: Instant?
}

interface ExchangeTimestampRow {
    val otherPartyId: UUID
    val createdAt: Instant
    // Whether the querying user (the `:userId` param this row came from) was the sender of this
    // particular photo — needed now that a streak day requires activity in both directions (see
    // StreakCalculator), not just any exchange.
    val sentByMe: Boolean
}

interface PhotoRecipientRepository : JpaRepository<PhotoRecipient, UUID> {

    // Was the authorization check for the reaction feature (see PhotoReactionService.setReaction,
    // commented out) — disabled alongside it rather than deleted.
    // fun existsByPhoto_IdAndRecipient_Id(photoId: UUID, recipientId: UUID): Boolean

    /** Who a photo has already been sent to — used by PhotoService.addRecipients to dedupe
     * against, so attaching recipients to an already-uploaded photo can never create a second
     * row (and a second push notification) for someone already on it. */
    fun findAllByPhoto_Id(photoId: UUID): List<PhotoRecipient>

    /** Every photo currently visible on the Home feed, across all senders: each sender's single
     * most recent photo (no age limit — it never expires on its own, see [graceSince]) plus any
     * of their earlier photos that haven't finished their 24-hour grace period yet.
     *
     * A sender's photo stops being "the latest" the moment they send a newer one — that's when
     * its own 24-hour countdown starts, not from when it was originally sent. `lead(created_at)`
     * gets each row its own immediate successor's timestamp within that sender's photos; a row
     * with no successor (`next_created_at is null`) is that sender's current latest and always
     * shows, and a row whose successor arrived within the last 24 hours (`next_created_at >=
     * :graceSince`) is still finishing its grace period. Once a photo's successor is more than
     * 24 hours old, that photo drops out. */
    @Query(
        value = """
            select photo_id as photoId, sender_id as senderId, sender_display_name as senderDisplayName,
                   sender_profile_photo_storage_key as senderProfilePhotoStorageKey, storage_key as storageKey,
                   content_type as contentType, created_at as createdAt, viewed_at as viewedAt
            from (
                select
                    p.id                                as photo_id,
                    p.sender_id                          as sender_id,
                    u.display_name                       as sender_display_name,
                    u.profile_photo_storage_key          as sender_profile_photo_storage_key,
                    p.storage_key                        as storage_key,
                    p.content_type                       as content_type,
                    p.created_at                         as created_at,
                    pr.viewed_at                          as viewed_at,
                    lead(p.created_at) over (partition by p.sender_id order by p.created_at asc) as next_created_at
                from photo_recipients pr
                join photos p on p.id = pr.photo_id
                join users u on u.id = p.sender_id
                where pr.recipient_id = :recipientId
            ) ranked
            where next_created_at is null or next_created_at >= :graceSince
            order by sender_id, created_at asc
        """,
        nativeQuery = true,
    )
    fun findVisibleFeedPhotos(@Param("recipientId") recipientId: UUID, @Param("graceSince") graceSince: Instant): List<FeedRow>

    @Query(
        value = """
            select
                p.id                              as photoId,
                p.sender_id                       as senderId,
                u.display_name                    as senderDisplayName,
                u.profile_photo_storage_key        as senderProfilePhotoStorageKey,
                p.storage_key                      as storageKey,
                p.content_type                     as contentType,
                p.created_at                       as createdAt,
                pr.viewed_at                       as viewedAt
            from photo_recipients pr
            join photos p on p.id = pr.photo_id
            join users u on u.id = p.sender_id
            where pr.recipient_id = :recipientId
            order by p.created_at desc
            limit :limit
        """,
        nativeQuery = true,
    )
    fun findRecentReceived(@Param("recipientId") recipientId: UUID, @Param("limit") limit: Int): List<FeedRow>

    @Query(
        value = """
            select p.created_at as createdAt
            from photo_recipients pr
            join photos p on p.id = pr.photo_id
            where (p.sender_id = :userA and pr.recipient_id = :userB)
               or (p.sender_id = :userB and pr.recipient_id = :userA)
            order by p.created_at desc
        """,
        nativeQuery = true,
    )
    fun findExchangeTimestamps(
        @Param("userA") userA: UUID,
        @Param("userB") userB: UUID,
    ): List<Instant>

    /** Batched form of [findExchangeTimestamps] for computing every friend's streak/last-activity
     * at once instead of one query per friend — used by FriendService/ActivityService/
     * PhotoService.getFeed, each of which used to call [findExchangeTimestamps] in a loop over
     * every friend on every cache-miss request. Group the result by [ExchangeTimestampRow.otherPartyId]. */
    @Query(
        value = """
            select
                case when p.sender_id = :userId then pr.recipient_id else p.sender_id end as otherPartyId,
                p.created_at as createdAt,
                (p.sender_id = :userId) as sentByMe
            from photo_recipients pr
            join photos p on p.id = pr.photo_id
            where (p.sender_id = :userId and pr.recipient_id in :otherIds)
               or (pr.recipient_id = :userId and p.sender_id in :otherIds)
        """,
        nativeQuery = true,
    )
    fun findExchangeTimestampsBatch(
        @Param("userId") userId: UUID,
        @Param("otherIds") otherIds: Collection<UUID>,
    ): List<ExchangeTimestampRow>

    /** Marks one specific (photo, recipient) pair as viewed — scoped to that single row, so one
     * recipient viewing a photo sent to several people never affects any other recipient's own
     * viewed state for that same photo. A no-op update (returns 0) if it was already viewed,
     * which keeps the very first view's timestamp rather than sliding it forward on a re-view. */
    @Modifying
    @Query(
        value = """
            update photo_recipients
            set viewed_at = :viewedAt
            where photo_id = :photoId and recipient_id = :recipientId and viewed_at is null
        """,
        nativeQuery = true,
    )
    fun markViewed(
        @Param("photoId") photoId: UUID,
        @Param("recipientId") recipientId: UUID,
        @Param("viewedAt") viewedAt: Instant,
    ): Int
}
