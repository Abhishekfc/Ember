package com.ember.backend.service

import com.ember.backend.dto.FeedItem
import com.ember.backend.dto.PhotoEntry
import com.ember.backend.dto.PhotoUploadResponse
import com.ember.backend.exception.InvalidFriendRequestException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.FriendshipStatus
import com.ember.backend.model.Photo
import com.ember.backend.model.PhotoRecipient
import com.ember.backend.repository.FriendshipRepository
import com.ember.backend.repository.PhotoRecipientRepository
import com.ember.backend.repository.PhotoRepository
import com.ember.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
private const val FEED_WINDOW_HOURS = 24L

@Service
class PhotoService(
    private val photoRepository: PhotoRepository,
    private val photoRecipientRepository: PhotoRecipientRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val r2StorageService: R2StorageService,
    private val pushNotificationService: PushNotificationService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun upload(senderId: UUID, file: MultipartFile, recipientIds: List<UUID>): PhotoUploadResponse {
        if (recipientIds.isEmpty()) {
            throw InvalidFriendRequestException("At least one recipient is required")
        }
        val contentType = file.contentType
        if (contentType == null || contentType !in ALLOWED_CONTENT_TYPES) {
            throw InvalidFriendRequestException("Unsupported content type: $contentType")
        }

        val sender = userRepository.findById(senderId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        recipientIds.forEach { recipientId ->
            val friendship = friendshipRepository.findBetween(senderId, recipientId)
            if (friendship == null || friendship.status != FriendshipStatus.ACCEPTED) {
                throw InvalidFriendRequestException("Recipient $recipientId is not an accepted friend")
            }
        }

        val extension = when (contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val storageKey = "photos/$senderId/${UUID.randomUUID()}.$extension"
        r2StorageService.upload(storageKey, contentType, file.bytes)

        val photo = photoRepository.save(
            Photo(sender = sender, storageKey = storageKey, contentType = contentType)
        )

        val recipients = recipientIds.map { recipientId ->
            val recipient = userRepository.findById(recipientId)
                .orElseThrow { ResourceNotFoundException("Recipient not found") }
            photoRecipientRepository.save(
                PhotoRecipient(photo = photo, recipient = recipient, deliveredAt = Instant.now())
            )
        }

        pushNotificationService.notifyNewPhoto(
            senderDisplayName = sender.displayName,
            recipientUserIds = recipients.map { it.recipient.id },
        )
        logger.info(
            "Photo uploaded: photoId={} sender={} ({}) recipients={} sizeBytes={}",
            photo.id, sender.id, sender.email, recipientIds, file.size,
        )

        return PhotoUploadResponse(
            photoId = photo.id,
            url = r2StorageService.publicUrl(storageKey),
            createdAt = photo.createdAt,
            recipientIds = recipients.map { it.recipient.id },
        )
    }

    /** Every photo a friend sent in the last 24 hours, Snapchat-style — not just their latest
     * one. Photos older than the window simply stop appearing here (consistent with the
     * deliberate no-Memories/no-resurfacing-old-photos stance); nothing is deleted from storage. */
    fun getFeed(userId: UUID): List<FeedItem> {
        val acceptedFriendIds = friendshipRepository.findAllForUserWithStatus(userId, FriendshipStatus.ACCEPTED)
            .map { if (it.requester.id == userId) it.addressee.id else it.requester.id }
            .toSet()

        val since = Instant.now().minus(FEED_WINDOW_HOURS, ChronoUnit.HOURS)
        val rows = photoRecipientRepository.findRecentPhotos(userId, since)
            .filter { it.senderId in acceptedFriendIds }

        return rows.groupBy { it.senderId }.map { (senderId, senderRows) ->
            val exchangeTimestamps = photoRecipientRepository.findExchangeTimestamps(userId, senderId)
            val photos = senderRows.sortedBy { it.createdAt }.map {
                PhotoEntry(photoId = it.photoId, photoUrl = r2StorageService.publicUrl(it.storageKey), createdAt = it.createdAt)
            }
            FeedItem(
                friendId = senderId,
                displayName = senderRows.first().senderDisplayName,
                photos = photos,
                streak = StreakCalculator.compute(exchangeTimestamps),
            )
        }.sortedByDescending { it.photos.last().createdAt }
    }
}
