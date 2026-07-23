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
}

interface PhotoRecipientRepository : JpaRepository<PhotoRecipient, UUID> {

    /** Every photo received in the last [since] window, across all senders — used for the
     * Snapchat-style Home feed where a friend can have several photos to page through rather
     * than just their single latest one. */
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
            and p.created_at >= :since
            order by p.sender_id, p.created_at asc
        """,
        nativeQuery = true,
    )
    fun findRecentPhotos(@Param("recipientId") recipientId: UUID, @Param("since") since: Instant): List<FeedRow>

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
                p.created_at as createdAt
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
