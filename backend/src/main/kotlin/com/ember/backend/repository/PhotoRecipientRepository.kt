package com.ember.backend.repository

import com.ember.backend.model.PhotoRecipient
import org.springframework.data.jpa.repository.JpaRepository
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
                p.created_at                       as createdAt
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
                p.created_at                       as createdAt
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
}
