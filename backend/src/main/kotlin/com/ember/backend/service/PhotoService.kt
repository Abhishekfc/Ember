package com.ember.backend.service

import com.ember.backend.dto.FeedItem
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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")

@Service
class PhotoService(
    private val photoRepository: PhotoRepository,
    private val photoRecipientRepository: PhotoRecipientRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val r2StorageService: R2StorageService,
    private val pushNotificationService: PushNotificationService,
) {

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

        return PhotoUploadResponse(
            photoId = photo.id,
            url = r2StorageService.publicUrl(storageKey),
            createdAt = photo.createdAt,
            recipientIds = recipients.map { it.recipient.id },
        )
    }

    fun getFeed(userId: UUID): List<FeedItem> =
        photoRecipientRepository.findLatestPhotoPerSender(userId).map { row ->
            val exchangeTimestamps = photoRecipientRepository.findExchangeTimestamps(userId, row.senderId)
            FeedItem(
                friendId = row.senderId,
                displayName = row.senderDisplayName,
                photoUrl = r2StorageService.publicUrl(row.storageKey),
                createdAt = row.createdAt,
                streak = StreakCalculator.compute(exchangeTimestamps),
            )
        }
}
