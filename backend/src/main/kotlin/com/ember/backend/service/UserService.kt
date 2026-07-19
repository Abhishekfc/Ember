package com.ember.backend.service

import com.ember.backend.dto.UpdateProfileRequest
import com.ember.backend.dto.UserProfile
import com.ember.backend.exception.InvalidFriendRequestException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.exception.UsernameAlreadyTakenException
import com.ember.backend.model.User
import com.ember.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")

@Service
class UserService(
    private val userRepository: UserRepository,
    private val r2StorageService: R2StorageService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getProfile(userId: UUID): UserProfile {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }
        return user.toProfile()
    }

    @Transactional
    fun updateProfilePhoto(userId: UUID, file: MultipartFile): UserProfile {
        val contentType = file.contentType
        if (contentType == null || contentType !in ALLOWED_CONTENT_TYPES) {
            throw InvalidFriendRequestException("Unsupported content type: $contentType")
        }

        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val extension = when (contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val storageKey = "profile-photos/$userId/${UUID.randomUUID()}.$extension"
        r2StorageService.upload(storageKey, contentType, file.bytes)

        user.profilePhotoStorageKey = storageKey
        userRepository.save(user)
        logger.info("Profile photo updated: userId={}", userId)

        return user.toProfile()
    }

    @Transactional
    fun updateProfile(userId: UUID, request: UpdateProfileRequest): UserProfile {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        request.displayName?.trim()?.let { trimmed ->
            if (trimmed.isBlank()) throw InvalidFriendRequestException("Display name cannot be blank")
            user.displayName = trimmed
        }

        request.username?.trim()?.lowercase()?.let { normalized ->
            if (normalized.isBlank()) throw InvalidFriendRequestException("Username cannot be blank")
            if (normalized != user.username && userRepository.existsByUsername(normalized)) {
                throw UsernameAlreadyTakenException()
            }
            user.username = normalized
        }

        userRepository.save(user)
        logger.info("Profile updated: userId={}", userId)
        return user.toProfile()
    }

    private fun User.toProfile() = UserProfile(
        userId = id,
        displayName = displayName,
        username = username,
        email = email,
        profilePhotoUrl = profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
    )
}
