package com.ember.backend.service

import com.ember.backend.dto.FeedItem
import com.ember.backend.dto.MemoryPhoto
import com.ember.backend.dto.PhotoEntry
import com.ember.backend.dto.PhotoUploadResponse
import com.ember.backend.dto.SentPhoto
import com.ember.backend.exception.InvalidFriendRequestException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.exception.UnsendWindowExpiredException
import com.ember.backend.model.FriendshipStatus
import com.ember.backend.model.User
import com.ember.backend.repository.FriendshipRepository
import com.ember.backend.repository.PhotoRecipientRepository
import com.ember.backend.repository.PhotoRepository
import com.ember.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")

// See PhotoRecipientRepository.findVisibleFeedPhotos: a sender's latest photo never expires on its
// own. This is how long an OLDER photo of theirs stays up after a newer one supersedes it, not a
// fixed age limit on the photo.
private const val PHOTO_GRACE_PERIOD_HOURS = 24L

/** Caps `recipientIds`. Without a cap, one request could drive an arbitrarily large number of
 * sequential DB round trips. No real send needs more than a modest part of a friend list. */
private const val MAX_RECIPIENTS_PER_PHOTO = 50

/**
 * Widest date range a single Memories request may span. A wider request is narrowed to this, not
 * rejected.
 *
 * `start` and `end` come straight off the query string, so `start=1970&end=3000` would return every
 * photo an account has ever saved in one response, as often as the caller likes. That needs a bound.
 *
 * The first attempt capped the span at 366 days on the assumption that the client asks for one
 * calendar month. But on first load, before the account's creation date is known, the client asks
 * for the last `MEMORIES_HISTORY_LIMIT_YEARS` (5) years at once, so the cap rejected the app's own
 * opening request. It also rejected instead of clamping, turning that into a 400 the client shows
 * as "Couldn't connect": a hard failure of the whole screen over a parameter the server could have
 * narrowed.
 *
 * Ten years clears that five-year request and is longer than this app has existed, so clamping can't
 * drop a photo anyone has; it only stops an absurd range.
 */
private const val MAX_MEMORIES_RANGE_DAYS = 3650L

@Service
class PhotoService(
    private val photoRepository: PhotoRepository,
    private val photoRecipientRepository: PhotoRecipientRepository,
    // Reaction feature disabled — see PhotoReactionService's own comment.
    // private val photoReactionRepository: PhotoReactionRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val r2StorageService: R2StorageService,
    private val photoWriteService: PhotoWriteService,
    private val pushNotificationService: PushNotificationService,
    private val cacheManager: CacheManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Not `@Transactional`: the DB writes run in their own short transaction in
     * [PhotoWriteService], separate from the R2 upload and the FCM push. Neither external call has
     * a timeout, and when they shared the method's transaction they held a pooled DB connection for
     * its whole duration. A burst of uploads during a storage or FCM slowdown could exhaust the
     * pool and take down unrelated requests. This way a slow external call blocks only its own
     * request. */
    fun upload(senderId: UUID, file: MultipartFile, recipientIds: List<UUID>, save: Boolean): PhotoUploadResponse {
        val distinctRecipientIds = recipientIds.distinct()
        // A photo needs somewhere to go: at least one recipient, or an explicit save (the camera's
        // bookmark button with no one selected). Never neither.
        if (distinctRecipientIds.isEmpty() && !save) {
            throw InvalidFriendRequestException("Select a recipient or save to Memories")
        }
        if (distinctRecipientIds.size > MAX_RECIPIENTS_PER_PHOTO) {
            throw InvalidFriendRequestException("Too many recipients (max $MAX_RECIPIENTS_PER_PHOTO)")
        }
        val contentType = file.contentType
        if (contentType == null || contentType !in ALLOWED_CONTENT_TYPES) {
            throw InvalidFriendRequestException("Unsupported content type: $contentType")
        }
        // Read once and reused: `MultipartFile.getBytes()` re-materializes the whole upload on every
        // call (from disk, past Spring's in-memory threshold). This used to be called twice (sniff,
        // then compress), doubling the bytes moved and the peak heap for large uploads.
        val uploadedBytes = file.bytes
        val detectedType = ImageContentSniffer.detect(uploadedBytes)
        if (detectedType == null || detectedType !in ALLOWED_CONTENT_TYPES) {
            throw InvalidFriendRequestException("File content doesn't match a supported image type")
        }
        // Re-encodes to a sane size and format before storing, so every later fetch is reasonably
        // sized whatever was uploaded. Conservative: an already-small JPEG passes through untouched
        // (see PhotoCompressionService).
        val compressed = PhotoCompressionService.compress(uploadedBytes, detectedType)

        val sender = userRepository.findById(senderId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        // Skipped for a save-only upload: with no recipients there are no friendships to check.
        val recipients = if (distinctRecipientIds.isNotEmpty()) {
            validateAcceptedFriends(senderId, distinctRecipientIds)
        } else {
            emptyList()
        }

        // The extension follows the type actually stored (after compression), not what the client's
        // multipart part claimed: a client can label any content "image/jpeg", and that declared
        // type used to be trusted all the way through to what gets served back.
        val extension = when (compressed.contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val storageKey = "photos/$senderId/${UUID.randomUUID()}.$extension"
        r2StorageService.upload(storageKey, compressed.contentType, compressed.bytes)

        val (photo, photoRecipients) = photoWriteService.persist(sender, storageKey, compressed.contentType, recipients, save)

        pushNotificationService.notifyNewPhoto(
            photoId = photo.id,
            photoUrl = r2StorageService.publicUrl(storageKey),
            senderId = sender.id,
            senderDisplayName = sender.displayName,
            createdAt = photo.createdAt,
            recipientUserIds = photoRecipients.map { it.recipient.id },
        )
        logger.info(
            "Photo uploaded: photoId={} sender={} ({}) recipients={} saved={} uploadedBytes={} storedBytes={}",
            photo.id, sender.id, sender.email, distinctRecipientIds, save, file.size, compressed.bytes.size,
        )

        // A new photo changes each recipient's feed and the streak on both sides of every
        // sender/recipient pair. The sender's own feed is unaffected (it never includes their own
        // photos). All of this is a no-op for a save-only upload, since distinctRecipientIds is
        // empty.
        cacheManager.getCache("feed")?.let { cache -> distinctRecipientIds.forEach { cache.evict(it.toString()) } }
        cacheManager.getCache("friends")?.let { cache ->
            cache.evict(senderId.toString())
            distinctRecipientIds.forEach { cache.evict(it.toString()) }
        }
        // Recipients get a new PHOTO_RECEIVED event, and the sender's own streak-expiring risk can
        // change the moment they send (today's exchange is now covered), so evict both sides.
        cacheManager.getCache("activity")?.let { cache ->
            cache.evict(senderId.toString())
            distinctRecipientIds.forEach { cache.evict(it.toString()) }
        }

        return PhotoUploadResponse(
            photoId = photo.id,
            url = r2StorageService.publicUrl(storageKey),
            createdAt = photo.createdAt,
            recipientIds = photoRecipients.map { it.recipient.id },
            saved = photo.savedAt != null,
        )
    }

    // Shared by upload() and addRecipients(): the accepted-friend and existence checks any set of
    // recipient ids needs before it can be attached to a photo.
    private fun validateAcceptedFriends(senderId: UUID, distinctRecipientIds: List<UUID>): List<User> {
        // One query for all recipients instead of one per recipient (see
        // FriendshipRepository.findAllWithStatusBetween).
        val acceptedFriendships = friendshipRepository.findAllWithStatusBetween(senderId, distinctRecipientIds, FriendshipStatus.ACCEPTED)
        val acceptedOtherPartyIds = acceptedFriendships.mapTo(mutableSetOf()) {
            if (it.requester.id == senderId) it.addressee.id else it.requester.id
        }
        val notFriends = distinctRecipientIds.filterNot { it in acceptedOtherPartyIds }
        if (notFriends.isNotEmpty()) {
            throw InvalidFriendRequestException("Recipient(s) $notFriends are not accepted friends")
        }

        val found = userRepository.findAllById(distinctRecipientIds)
        if (found.size != distinctRecipientIds.size) {
            throw ResourceNotFoundException("One or more recipients not found")
        }
        return found
    }

    /** Turns an already-uploaded photo's saved_at on, with no re-upload; the counterpart to
     * [addRecipients]. Together they let the camera's bookmark and Send actions share one real
     * upload instead of each uploading the same file when both are tapped for one capture (see
     * CameraViewModel.queueUpload on the client, and AttachPhotoWorker, which chains the two so
     * this only runs once the original upload has landed). Idempotent: a photo that is already
     * saved keeps its original timestamp. */
    fun markSaved(ownerId: UUID, photoId: UUID) {
        val photo = photoRepository.findById(photoId).orElse(null) ?: throw ResourceNotFoundException("Photo not found")
        if (photo.sender.id != ownerId) {
            throw ResourceNotFoundException("Photo not found")
        }
        if (photo.savedAt == null) {
            photo.savedAt = Instant.now()
            photoRepository.save(photo)
        }
    }

    /** Adds recipients to an already-uploaded photo, with no re-upload (see [markSaved]). People the
     * photo was already sent to are skipped, so an edited recipient list or a retried request never
     * creates a second PhotoRecipient row for the same person. Only the genuinely new recipients
     * get a push and a cache eviction: someone already notified about this photo shouldn't be
     * notified twice. */
    fun addRecipients(ownerId: UUID, photoId: UUID, recipientIds: List<UUID>) {
        val photo = photoRepository.findById(photoId).orElse(null) ?: throw ResourceNotFoundException("Photo not found")
        if (photo.sender.id != ownerId) {
            throw ResourceNotFoundException("Photo not found")
        }
        val distinctRecipientIds = recipientIds.distinct()
        if (distinctRecipientIds.isEmpty()) return
        if (distinctRecipientIds.size > MAX_RECIPIENTS_PER_PHOTO) {
            throw InvalidFriendRequestException("Too many recipients (max $MAX_RECIPIENTS_PER_PHOTO)")
        }

        val existingRecipientIds = photoRecipientRepository.findAllByPhoto_Id(photoId).map { it.recipient.id }.toSet()
        val newRecipientIds = distinctRecipientIds.filterNot { it in existingRecipientIds }
        if (newRecipientIds.isEmpty()) return

        val newRecipients = validateAcceptedFriends(ownerId, newRecipientIds)
        val newPhotoRecipients = photoWriteService.addRecipients(photo, newRecipients)

        pushNotificationService.notifyNewPhoto(
            photoId = photo.id,
            photoUrl = r2StorageService.publicUrl(photo.storageKey),
            senderId = ownerId,
            senderDisplayName = photo.sender.displayName,
            createdAt = photo.createdAt,
            recipientUserIds = newPhotoRecipients.map { it.recipient.id },
        )

        cacheManager.getCache("feed")?.let { cache -> newRecipientIds.forEach { cache.evict(it.toString()) } }
        cacheManager.getCache("friends")?.let { cache ->
            cache.evict(ownerId.toString())
            newRecipientIds.forEach { cache.evict(it.toString()) }
        }
        cacheManager.getCache("activity")?.let { cache ->
            cache.evict(ownerId.toString())
            newRecipientIds.forEach { cache.evict(it.toString()) }
        }
    }

    /** Immediate, real deletion: either Memories' delete (a saved photo, any time; unlike
     * PhotoCleanupService's scheduled pass, it doesn't wait for every recipient's feed visibility to
     * expire) or the Camera outbox's Unsend (an unsaved, recently sent photo, gated below). The
     * lookup is scoped to [ownerId] itself, not just checked after loading, so one account can
     * never delete another's photo by guessing an id.
     *
     * Recipients (for cache eviction and the push below) are read from whoever the photo was
     * actually sent to, not inferred from its saved state: a photo can be both saved to Memories
     * and sent to recipients (see [addRecipients]), so "was it saved" says nothing about whether
     * anyone else has it in their feed. The ids are captured before the delete because PhotoRecipient
     * rows cascade-delete with their Photo; this is the last point they can be read. Empty for a
     * photo that was never sent, which naturally skips the block below. */
    fun delete(ownerId: UUID, photoId: UUID) {
        val photo = photoRepository.findById(photoId).orElse(null) ?: return
        if (photo.sender.id != ownerId) {
            throw ResourceNotFoundException("Photo not found")
        }
        // A real restriction only for an unsaved photo; a saved one is Memories' permanent-delete
        // flow and was never subject to it. It uses the same PHOTO_GRACE_PERIOD_HOURS window that
        // decides how long a photo stays in the outbox list (see PhotoRepository's query), so
        // anything the list still shows can always be unsent.
        if (photo.savedAt == null) {
            val unsendDeadline = photo.createdAt.plus(PHOTO_GRACE_PERIOD_HOURS, ChronoUnit.HOURS)
            if (Instant.now().isAfter(unsendDeadline)) {
                throw UnsendWindowExpiredException()
            }
        }
        val recipientIds = photoRecipientRepository.findAllByPhoto_Id(photoId).map { it.recipient.id }
        r2StorageService.delete(photo.storageKey)
        photoRepository.delete(photo)

        if (recipientIds.isNotEmpty()) {
            // Same as upload()'s eviction: a recipient's feed (and the "latest sent photo" in the
            // friends summary) must stop reflecting a deleted photo at once, not age out later.
            cacheManager.getCache("feed")?.let { cache -> recipientIds.forEach { cache.evict(it.toString()) } }
            cacheManager.getCache("friends")?.let { cache -> recipientIds.forEach { cache.evict(it.toString()) } }
            pushNotificationService.notifyPhotoDeleted(
                photoId = photo.id,
                senderId = ownerId,
                recipientUserIds = recipientIds,
            )
        }
    }

    /** This account's outbox: recently sent, unsaved photos still inside their unsend window. The
     * bound is the last 24 hours (not the feed's more permissive "still visible to a recipient"
     * rule) because this list exists to offer Unsend, and a photo past that window (see [delete])
     * has no reason to keep showing. */
    fun getRecentSent(userId: UUID): List<SentPhoto> {
        val since = Instant.now().minus(PHOTO_GRACE_PERIOD_HOURS, ChronoUnit.HOURS)
        return photoRepository.findBySenderIdAndSavedAtIsNullAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(userId, since)
            .map { SentPhoto(photoId = it.id, photoUrl = r2StorageService.publicUrl(it.storageKey), createdAt = it.createdAt) }
    }

    /** The photos friends sent [userId] that are still visible: each sender's latest photo, plus
     * their older ones for [PHOTO_GRACE_PERIOD_HOURS] after a newer one superseded them (see
     * PhotoRecipientRepository.findVisibleFeedPhotos). A photo that stops showing is not deleted
     * from storage. Cached per user (see the evictions in [upload]) because this is the app's most
     * repeatedly fetched query: Home refetches it on every open, pull-to-refresh and post-send sync.
     *
     * [forceRefresh] (Home's pull-to-refresh, not the silent post-send reload) skips the cache read
     * but still writes the fresh result back. A plain `@Cacheable` can't express "bypass on read,
     * always repopulate on write", so this works on the Cache directly. Without it, pulling to
     * refresh inside the TTL would hand back the same stale snapshot it is meant to override. */
    fun getFeed(userId: UUID, forceRefresh: Boolean = false): List<FeedItem> {
        val cache = cacheManager.getCache("feed")
        val cacheKey = userId.toString()
        if (!forceRefresh) {
            // A failing cache read (for example a value that no longer deserializes, seen with an
            // empty-list result that GenericJackson2JsonRedisSerializer's polymorphic type wrapping
            // doesn't round-trip reliably) must never fail the request. It counts as a miss: fall
            // through and recompute instead of letting a bad cache entry become a 500.
            runCatching { cache?.get(cacheKey, List::class.java) }.getOrNull()?.let {
                @Suppress("UNCHECKED_CAST")
                return it as List<FeedItem>
            }
        }

        val acceptedFriendIds = friendshipRepository.findAllForUserWithStatus(userId, FriendshipStatus.ACCEPTED)
            .map { if (it.requester.id == userId) it.addressee.id else it.requester.id }
            .toSet()

        val graceSince = Instant.now().minus(PHOTO_GRACE_PERIOD_HOURS, ChronoUnit.HOURS)
        val rows = photoRecipientRepository.findVisibleFeedPhotos(userId, graceSince)
            .filter { it.senderId in acceptedFriendIds }

        val rowsBySender = rows.groupBy { it.senderId }
        // One query for every sender in the feed instead of one per sender.
        val timestampsBySender = if (rowsBySender.isEmpty()) {
            emptyMap()
        } else {
            photoRecipientRepository.findExchangeTimestampsBatch(userId, rowsBySender.keys)
                .groupBy({ it.otherPartyId }, { StreakExchange(it.createdAt, it.sentByMe) })
        }
        // Reaction feature disabled — this batched "my reaction per photo" lookup (see
        // PhotoReactionService's own comment) is commented out alongside it.
        // val myReactionByPhotoId = if (rows.isEmpty()) {
        //     emptyMap()
        // } else {
        //     photoReactionRepository.findMyReactions(userId, rows.map { it.photoId })
        //         .associate { it.photoId to it.emoji }
        // }

        val feed = rowsBySender.map { (senderId, senderRows) ->
            val exchangeTimestamps = timestampsBySender[senderId] ?: emptyList()
            val photos = senderRows.sortedBy { it.createdAt }.map {
                PhotoEntry(
                    photoId = it.photoId,
                    photoUrl = r2StorageService.publicUrl(it.storageKey),
                    createdAt = it.createdAt,
                    seen = it.viewedAt != null,
                )
            }
            FeedItem(
                friendId = senderId,
                displayName = senderRows.first().senderDisplayName,
                photos = photos,
                streak = StreakCalculator.compute(exchangeTimestamps),
            )
        }.sortedByDescending { it.photos.last().createdAt }

        cache?.put(cacheKey, feed)
        return feed
    }

    /** Marks one photo as viewed by [userId] only: it touches just that (photo, recipient) row, never
     * another recipient's viewed state for the same photo (see
     * [PhotoRecipientRepository.markViewed]). The feed is cached, so this evicts [userId]'s entry;
     * otherwise [getFeed] would keep returning the still-unseen snapshot for the rest of the TTL.
     *
     * `@Transactional` is required: [PhotoRecipientRepository.markViewed] is a `@Modifying` update,
     * and Hibernate throws `TransactionRequiredException` when one runs outside a transaction.
     * [upload] doesn't need it because its DB writes run inside [PhotoWriteService]'s transaction;
     * this method writes directly, so it needs its own. */
    @Transactional
    fun markSeen(userId: UUID, photoId: UUID) {
        photoRecipientRepository.markViewed(photoId, userId, Instant.now())
        cacheManager.getCache("feed")?.evict(userId.toString())
    }

    /** Photos [userId] saved within [start, end) (UTC instants), newest first, for the Memories
     * grid. A separate query from [getFeed] on purpose: the feed's short window is its own choice
     * not to resurface old photos on Home, not a limit to work around. Received photos don't belong
     * here: Memories is this user's own saved history, and only what was explicitly saved (see
     * Photo.savedAt), not everything they ever sent. Not cached, since it is only fetched when
     * Memories loads, unlike the feed and friends hot paths. The range is bounded by
     * MAX_MEMORIES_RANGE_DAYS, which is the only limit; there is no count cap, so a long-time user's
     * full history stays reachable. */
    fun getMemoriesInRange(userId: UUID, start: Instant, end: Instant): List<MemoryPhoto> {
        if (!end.isAfter(start)) return emptyList()
        // Narrowed, not refused (see MAX_MEMORIES_RANGE_DAYS): an over-wide request gets the most
        // recent slice of the range instead of an error, so it can never break the screen.
        val earliest = end.minus(MAX_MEMORIES_RANGE_DAYS, ChronoUnit.DAYS)
        val boundedStart = if (start.isBefore(earliest)) earliest else start
        return photoRepository.findBySenderIdAndSavedAtIsNotNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(userId, boundedStart, end)
            .map {
                MemoryPhoto(
                    photoId = it.id,
                    photoUrl = r2StorageService.publicUrl(it.storageKey),
                    createdAt = it.createdAt,
                )
            }
    }
}
