package com.ember.backend.repository

// Disabled alongside the rest of the reaction feature — see PhotoReactionService's own comment.

/*
import com.ember.backend.model.PhotoReaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface MyReactionRow {
    val photoId: UUID
    val emoji: String
}

interface ReceivedReactionRow {
    val photoId: UUID
    val photoStorageKey: String
    val reactorId: UUID
    val reactorDisplayName: String
    val reactorProfilePhotoStorageKey: String?
    val emoji: String
    val createdAt: Instant
}

interface PhotoReactionRepository : JpaRepository<PhotoReaction, UUID> {

    fun findByPhoto_IdAndReactor_Id(photoId: UUID, reactorId: UUID): PhotoReaction?

    /** Batched "what did I react with on each of these photos" — one query for a whole feed page
     * instead of one per photo, same reasoning as every other batch query in this codebase
     * (PhotoRecipientRepository.findExchangeTimestampsBatch etc). */
    @Query(
        value = """
            select photo_id as photoId, emoji as emoji
            from photo_reactions
            where reactor_id = :reactorId and photo_id in :photoIds
        """,
        nativeQuery = true,
    )
    fun findMyReactions(@Param("reactorId") reactorId: UUID, @Param("photoIds") photoIds: Collection<UUID>): List<MyReactionRow>

    /** Reactions other people have left on photos *I* sent — backs the Activity feed's
     * PHOTO_REACTION events (see ActivityService.computeActivity). */
    @Query(
        value = """
            select
                pr.photo_id                        as photoId,
                p.storage_key                      as photoStorageKey,
                pr.reactor_id                       as reactorId,
                u.display_name                      as reactorDisplayName,
                u.profile_photo_storage_key         as reactorProfilePhotoStorageKey,
                pr.emoji                            as emoji,
                pr.created_at                       as createdAt
            from photo_reactions pr
            join photos p on p.id = pr.photo_id
            join users u on u.id = pr.reactor_id
            where p.sender_id = :senderId
            order by pr.created_at desc
            limit :limit
        """,
        nativeQuery = true,
    )
    fun findRecentReactionsReceived(@Param("senderId") senderId: UUID, @Param("limit") limit: Int): List<ReceivedReactionRow>
}
*/
