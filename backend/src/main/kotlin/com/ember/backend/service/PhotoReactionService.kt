package com.ember.backend.service

// Disabled — the frontend reaction feature was reverted after it turned out too buggy, so this
// whole path (entity/repository/service/controller/DTOs/push/activity wiring, across several
// files) is commented out rather than deleted, in case it's revisited later. See:
// - model/PhotoReaction.kt, repository/PhotoReactionRepository.kt (also commented out)
// - PhotoRecipientRepository.existsByPhoto_IdAndRecipient_Id (commented out)
// - PhotosController's reaction endpoint, PhotoService's myReaction wiring, PhotoDtos'
//   SetReactionRequest/Response + PhotoEntry.myReaction, PushNotificationService.notifyPhotoReaction,
//   ActivityService's reaction events, ActivityDtos' PHOTO_REACTION, ApiExceptions'
//   InvalidReactionException (all commented out in their own files)
// The photo_reactions table migration (V6) is deliberately left in place — see PhotoReaction.kt's
// own comment for why.

/*
import com.ember.backend.exception.InvalidReactionException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.PhotoReaction
import com.ember.backend.repository.PhotoRecipientRepository
import com.ember.backend.repository.PhotoRepository
import com.ember.backend.repository.PhotoReactionRepository
import com.ember.backend.repository.UserRepository
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** The only reactions a client can ever send — matches the fixed set the Android client actually
 * shows (three quick-tap emoji + a small expanded grid), enforced here too so a request can't
 * smuggle in arbitrary text as an "emoji". Single source of truth on this side; the client has
 * its own matching list purely for what it renders. */
val ALLOWED_REACTION_EMOJIS = setOf(
    "❤️", "😂", "😮",
    "👍", "🙌", "🥹", "😍", "😭", "🤯", "👀", "💯", "🎉", "😢", "😡", "🤔",
)

@Service
class PhotoReactionService(
    private val photoReactionRepository: PhotoReactionRepository,
    private val photoRecipientRepository: PhotoRecipientRepository,
    private val photoRepository: PhotoRepository,
    private val userRepository: UserRepository,
    private val pushNotificationService: PushNotificationService,
    private val cacheManager: CacheManager,
) {

    /** Sets [reactorId]'s reaction on [photoId] to [emoji] — replacing any previous reaction from
     * them on this photo, or removing it entirely if it's the same emoji they already had (a
     * toggle, matching how the client's own tap gesture works). Returns the reactor's resulting
     * reaction (null if it was just removed).
     *
     * Only a photo's actual recipient may react to it — a photo's own sender never appears as
     * their own recipient (see PhotoRecipientRepository.existsByPhoto_IdAndRecipient_Id's own doc
     * comment), so this also naturally blocks reacting to your own sent photo without a separate
     * check for that specific case. */
    @Transactional
    fun setReaction(photoId: UUID, reactorId: UUID, emoji: String): String? {
        if (emoji !in ALLOWED_REACTION_EMOJIS) {
            throw InvalidReactionException("Unsupported reaction: $emoji")
        }
        if (!photoRecipientRepository.existsByPhoto_IdAndRecipient_Id(photoId, reactorId)) {
            throw InvalidReactionException("You can't react to a photo that wasn't sent to you")
        }

        val photo = photoRepository.findById(photoId).orElseThrow { ResourceNotFoundException("Photo not found") }

        val existing = photoReactionRepository.findByPhoto_IdAndReactor_Id(photoId, reactorId)
        if (existing != null && existing.emoji == emoji) {
            photoReactionRepository.delete(existing)
            evictCaches(reactorId, photo.sender.id)
            return null
        }

        val reactor = userRepository.findById(reactorId).orElseThrow { ResourceNotFoundException("User not found") }

        if (existing != null) {
            existing.emoji = emoji
            existing.createdAt = Instant.now()
            photoReactionRepository.save(existing)
        } else {
            photoReactionRepository.save(PhotoReaction(photo = photo, reactor = reactor, emoji = emoji))
        }

        evictCaches(reactorId, photo.sender.id)
        pushNotificationService.notifyPhotoReaction(
            reactorDisplayName = reactor.displayName,
            emoji = emoji,
            recipientUserId = photo.sender.id,
        )
        return emoji
    }

    /** [reactorId]'s own "feed" cache entry embeds their reaction on every photo in it (see
     * PhotoService.getFeed's myReaction field) — without evicting it here, re-fetching within the
     * cache's TTL would hand back the pre-reaction snapshot, visibly reverting a reaction that
     * was just set. [senderId]'s "activity" cache entry is what surfaces this reaction to them in
     * the first place (see ActivityService.computeActivity's PHOTO_REACTION events) — same
     * reasoning as PhotoService.upload evicting both sides for PHOTO_RECEIVED. */
    private fun evictCaches(reactorId: UUID, senderId: UUID) {
        cacheManager.getCache("feed")?.evict(reactorId.toString())
        cacheManager.getCache("activity")?.evict(senderId.toString())
    }
}
*/
