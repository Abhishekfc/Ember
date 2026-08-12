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
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")

// See PhotoRecipientRepository.findVisibleFeedPhotos: a sender's latest photo never expires on
// its own; this is how long an OLDER photo of theirs stays up after being superseded by that
// newer one, not a fixed age limit on the photo itself.
private const val PHOTO_GRACE_PERIOD_HOURS = 24L

/** Not enforced anywhere before — an unbounded `recipientIds` list meant a single request could
 * drive an arbitrarily large number of sequential DB round-trips (and, before batching, one
 * friendship check + one user lookup + one insert *per recipient*). No real send in this app
 * needs more than a modest fraction of a friend list at once. */
private const val MAX_RECIPIENTS_PER_PHOTO = 50

/**
 * Widest date range a single Memories request may span.
 *
 * `start`/`end` arrive straight off the query string, and the query behind them has no count
 * limit of its own — the reasoning being that "a single month is naturally small". That holds for
 * the client, which only ever asks for one calendar month, but nothing enforced it: `start=1970`,
 * `end=3000` returned every saved photo an account has ever had, in one unbounded response,
 * repeatable as fast as the caller likes. A generous multiple of the one month the client
 * actually requests, so no legitimate call is affected.
 */
private const val MAX_MEMORIES_RANGE_DAYS = 366L

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

    /** Not `@Transactional` any more — the DB writes have their own short transaction in
     * [PhotoWriteService], separate from the R2 upload (and the FCM push after it), neither of
     * which has any timeout configured and both of which used to run while a pooled DB
     * connection was checked out for the whole method. A burst of uploads during a storage/FCM
     * slowdown could exhaust the entire connection pool and take down unrelated requests along
     * with it — this way, a slow external call blocks only its own request. */
    fun upload(senderId: UUID, file: MultipartFile, recipientIds: List<UUID>, save: Boolean): PhotoUploadResponse {
        val distinctRecipientIds = recipientIds.distinct()
        // A photo needs somewhere to go: at least one recipient, or an explicit save (the
        // camera's bookmark button, no one selected to send to) — never neither, or this
        // request wouldn't do anything at all.
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
        // Read once and reused. `MultipartFile.getBytes()` re-materializes the entire upload every
        // time it's called (from disk, for anything past Spring's in-memory threshold), and this
        // method used to call it twice — sniff, then compress — so a burst of 25MB uploads moved
        // twice as many bytes and held twice the peak heap as it needed to.
        val uploadedBytes = file.bytes
        val detectedType = ImageContentSniffer.detect(uploadedBytes)
        if (detectedType == null || detectedType !in ALLOWED_CONTENT_TYPES) {
            throw InvalidFriendRequestException("File content doesn't match a supported image type")
        }
        // Re-encodes down to a sane size/format before anything gets stored, so every future
        // fetch of this photo — from any screen, any client — is reasonably sized regardless of
        // what was actually uploaded. See PhotoCompressionService's own doc comment for why this
        // is conservative (an already-small JPEG passes through untouched).
        val compressed = PhotoCompressionService.compress(uploadedBytes, detectedType)

        val sender = userRepository.findById(senderId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        // Skipped entirely for a save-only upload — an empty recipientIds list means there's
        // nothing to check a friendship for.
        val recipients = if (distinctRecipientIds.isNotEmpty()) {
            validateAcceptedFriends(senderId, distinctRecipientIds)
        } else {
            emptyList()
        }

        // Stored using the type actually being stored (post-compression), not whatever the
        // client's multipart part claimed — a client can label any content "image/jpeg" in the
        // request, and that declared type used to be trusted all the way through to what gets
        // served back.
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

        // A new photo changes each recipient's feed, and the exchange-timestamp-derived streak
        // on both sides of every sender/recipient pair — the sender's own feed is unaffected
        // (it never includes photos they sent themselves). Both loops are no-ops for a save-only
        // upload, since distinctRecipientIds is empty.
        cacheManager.getCache("feed")?.let { cache -> distinctRecipientIds.forEach { cache.evict(it.toString()) } }
        cacheManager.getCache("friends")?.let { cache ->
            cache.evict(senderId.toString())
            distinctRecipientIds.forEach { cache.evict(it.toString()) }
        }
        // Recipients get a new PHOTO_RECEIVED event; the sender's own streak-expiring risk can
        // also change the moment they send (today's exchange is now covered), so both sides.
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

    // Shared by upload() and addRecipients() — every accepted-friend check and existence lookup
    // a set of recipient ids needs before they can be attached to a photo, one way or another.
    private fun validateAcceptedFriends(senderId: UUID, distinctRecipientIds: List<UUID>): List<User> {
        // One query for every recipient instead of one query per recipient (see
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

    /** Flips an already-uploaded photo's saved_at on, with no re-upload — the counterpart to
     * [addRecipients] below. Together these two are what let the camera's bookmark and Send
     * actions share one real upload instead of each independently uploading the same file when
     * both get tapped for the same capture (see CameraViewModel.queueUpload's own doc comment on
     * the client side, and AttachPhotoWorker for how the two get chained via WorkManager so this
     * only ever runs once the original upload has actually landed). Idempotent — a photo that's
     * already saved is left alone rather than re-stamping the timestamp. */
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

    /** Adds recipients to an already-uploaded photo, with no re-upload — see [markSaved]'s own
     * doc comment for the fuller reasoning. Silently dedupes against whoever the photo was
     * already sent to (a recipient list edited between the two taps, or a retried request,
     * should never create a second PhotoRecipient row for the same person) and only pushes/
     * evicts caches for whoever's actually new — someone already notified about this same photo
     * shouldn't be notified about it twice. */
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

    /** Real, immediate deletion — either Memories' own delete (a saved photo, unrestricted, any
     * time — unlike PhotoCleanupService's own scheduled pass, this doesn't wait for every
     * recipient's feed visibility to expire first) or the Camera outbox's Unsend (an unsaved,
     * recently-sent photo, gated below). Scoped to [ownerId] in the lookup itself (not just
     * checked after loading) so one account can never delete another's photo by guessing an id. */
    fun delete(ownerId: UUID, photoId: UUID) {
        val photo = photoRepository.findById(photoId).orElse(null) ?: return
        if (photo.sender.id != ownerId) {
            throw ResourceNotFoundException("Photo not found")
        }
        // Only a real restriction for an unsaved photo — a saved one is Memories' own permanent-
        // delete flow (see this method's own outer doc comment) and was never subject to this.
        // Same PHOTO_GRACE_PERIOD_HOURS window that decides how long a photo keeps appearing in
        // the outbox list in the first place (see PhotoRepository's own query) — once it's aged
        // out of that list, it's aged out of being unsendable too, so this can never reject
        // something the list itself would still be showing.
        if (photo.savedAt == null) {
            val unsendDeadline = photo.createdAt.plus(PHOTO_GRACE_PERIOD_HOURS, ChronoUnit.HOURS)
            if (Instant.now().isAfter(unsendDeadline)) {
                throw UnsendWindowExpiredException()
            }
        }
        r2StorageService.delete(photo.storageKey)
        photoRepository.delete(photo)
    }

    /** This account's own outbox — recently sent, unsaved photos still within their unsend
     * window. See PhotoRepository's own query for why "last 24 hours" (not the feed's more
     * permissive "still visible to a recipient" rule) is the right bound: this list exists to
     * offer Unsend, and a photo that's aged out of being unsendable (see [delete] above) has no
     * reason to keep showing here either. */
    fun getRecentSent(userId: UUID): List<SentPhoto> {
        val since = Instant.now().minus(PHOTO_GRACE_PERIOD_HOURS, ChronoUnit.HOURS)
        return photoRepository.findBySenderIdAndSavedAtIsNullAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(userId, since)
            .map { SentPhoto(photoId = it.id, photoUrl = r2StorageService.publicUrl(it.storageKey), createdAt = it.createdAt) }
    }

    /** Every photo a friend sent in the last 24 hours, Snapchat-style — not just their latest
     * one. Photos older than the window simply stop appearing here (consistent with the
     * deliberate no-Memories/no-resurfacing-old-photos stance); nothing is deleted from storage.
     * Cached per-user (see [upload]'s eviction) since this is the single most repeatedly-fetched
     * query in the app — Home refetches it on every open, pull-to-refresh, and post-send sync.
     *
     * [forceRefresh] (set from Home's pull-to-refresh gesture, not the silent post-send reload)
     * skips the cache *read* but still writes the fresh result back — a plain `@Cacheable` can't
     * express "bypass on read, always repopulate on write" in one annotation, so this is done by
     * hand against the Cache directly rather than declaratively. Without this, pulling to refresh
     * within the TTL window would silently hand back the same stale snapshot the gesture is
     * meant to override. */
    fun getFeed(userId: UUID, forceRefresh: Boolean = false): List<FeedItem> {
        val cache = cacheManager.getCache("feed")
        val cacheKey = userId.toString()
        if (!forceRefresh) {
            // A cache read failing (e.g. a value that no longer deserializes cleanly — seen in
            // practice with an empty-list result, which GenericJackson2JsonRedisSerializer's
            // polymorphic type-wrapping doesn't round-trip reliably) must never fail the request
            // itself — it's equivalent to a miss, so fall through and recompute instead of letting
            // a corrupt/incompatible cache entry surface as a 500.
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
        // One query covering every sender in today's feed instead of one query per sender.
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

    /** Marks a single photo as viewed by [userId] specifically — scoped to that one (photo,
     * recipient) row, so this never touches any other recipient's own viewed state for the same
     * photo (see [PhotoRecipientRepository.markViewed]). The feed is cached, so a mark-seen has
     * to evict [userId]'s entry or the next [getFeed] call would keep handing back the
     * now-stale (still-unseen) snapshot for the rest of the cache's TTL.
     *
     * `@Transactional` is required here, not optional — [PhotoRecipientRepository.markViewed] is
     * a `@Modifying` update query, and Hibernate throws `TransactionRequiredException` executing
     * one outside a transaction. Every other write in this class goes through [upload], which is
     * already `@Transactional`; this was the first standalone write and needed its own. */
    @Transactional
    fun markSeen(userId: UUID, photoId: UUID) {
        photoRecipientRepository.markViewed(photoId, userId, Instant.now())
        cacheManager.getCache("feed")?.evict(userId.toString())
    }

    /** Everything for the Memories grid within one date range (one calendar month, computed
     * client-side in local time and passed here as UTC instants) — deliberately a separate query
     * from [getFeed] rather than reusing it, since the Home feed's short window is its own
     * intentional "don't resurface old photos there" choice, not a limitation to work around.
     * Received photos deliberately don't belong here — Memories is this user's own saved
     * history, not an archive of everyone else's, and not even everything this user has ever
     * sent any more (see Photo.savedAt) — only what was explicitly saved. Not cached: unlike the
     * feed/friends hot paths, this is only fetched when a given month is actually opened, and the
     * client caches each month's result itself for the rest of the session once fetched. No
     * total-count cap unlike the old top-200-then-paginate approach — a single month is naturally
     * small, so nothing here needs bounding, and a long-time user's history further back stays
     * fully reachable. */
    fun getMemoriesInRange(userId: UUID, start: Instant, end: Instant): List<MemoryPhoto> {
        if (!end.isAfter(start)) {
            throw InvalidFriendRequestException("end must be after start")
        }
        if (Duration.between(start, end).toDays() > MAX_MEMORIES_RANGE_DAYS) {
            throw InvalidFriendRequestException("That date range is too wide")
        }
        return photoRepository.findBySenderIdAndSavedAtIsNotNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(userId, start, end)
            .map {
                MemoryPhoto(
                    photoId = it.id,
                    photoUrl = r2StorageService.publicUrl(it.storageKey),
                    createdAt = it.createdAt,
                )
            }
    }
}
