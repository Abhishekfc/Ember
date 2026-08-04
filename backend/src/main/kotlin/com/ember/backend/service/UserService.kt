package com.ember.backend.service

import com.ember.backend.dto.EmailAvailability
import com.ember.backend.dto.UpdateProfileRequest
import com.ember.backend.dto.UsernameAvailability
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
import kotlin.random.Random

private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
private val USERNAME_FORMAT = Regex("^[a-zA-Z0-9_.]+\$")
private const val USERNAME_MIN_LENGTH = 3
private const val USERNAME_MAX_LENGTH = 30
private const val MAX_SUGGESTIONS = 3

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
        // Sniffs the actual bytes rather than trusting the client-declared Content-Type, which a
        // raw API call can set to anything regardless of what's actually in the file.
        val detectedType = ImageContentSniffer.detect(file.bytes)
        if (detectedType == null || detectedType !in ALLOWED_CONTENT_TYPES) {
            throw InvalidFriendRequestException("File content doesn't match a supported image type")
        }

        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val extension = when (detectedType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val storageKey = "profile-photos/$userId/${UUID.randomUUID()}.$extension"
        r2StorageService.upload(storageKey, detectedType, file.bytes)

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

    /**
     * Read-only check so the client can tell someone their chosen username is taken *before*
     * they hit save, rather than only finding out from a failed update. Every lookup here —
     * the candidate itself, and each suggestion — is an exact-match `existsByUsername`, backed
     * by `idx_users_username`; none of this ever scans the full users table the way
     * `FriendService.searchUsers`'s substring search does.
     */
    fun checkUsernameAvailability(userId: UUID, candidate: String): UsernameAvailability {
        val normalized = candidate.trim().lowercase()
        if (normalized.length < USERNAME_MIN_LENGTH || normalized.length > USERNAME_MAX_LENGTH || !USERNAME_FORMAT.matches(normalized)) {
            return UsernameAvailability(available = false)
        }

        val user = userRepository.findById(userId).orElseThrow { ResourceNotFoundException("User not found") }
        if (normalized == user.username || !userRepository.existsByUsername(normalized)) {
            return UsernameAvailability(available = true)
        }

        return UsernameAvailability(available = false, suggestions = generateUsernameSuggestions(normalized))
    }

    /** Same check as [checkUsernameAvailability], for the registration flow specifically —
     * there's no signed-in user yet at that point (no [userId] to exclude a "keep my own current
     * username" case for), so this is a plain existence check with no self-exclusion. */
    fun checkUsernameAvailabilityPublic(candidate: String): UsernameAvailability {
        val normalized = candidate.trim().lowercase()
        if (normalized.length < USERNAME_MIN_LENGTH || normalized.length > USERNAME_MAX_LENGTH || !USERNAME_FORMAT.matches(normalized)) {
            return UsernameAvailability(available = false)
        }

        if (!userRepository.existsByUsername(normalized)) {
            return UsernameAvailability(available = true)
        }

        return UsernameAvailability(available = false, suggestions = generateUsernameSuggestions(normalized))
    }

    /** Whether an email can still be registered. Mirrors [checkUsernameAvailabilityPublic] and is
     * deliberately the same normalization AuthService.register applies (trim + lowercase), so the
     * answer this gives during sign-up matches what registration will actually decide — otherwise
     * someone could be told an address is free and still be rejected at the final step. */
    fun checkEmailAvailabilityPublic(candidate: String): EmailAvailability {
        val normalized = candidate.trim().lowercase()
        if (normalized.isEmpty()) return EmailAvailability(available = false)
        return EmailAvailability(available = !userRepository.existsByEmail(normalized))
    }

    private fun generateUsernameSuggestions(base: String): List<String> {
        val trimmedBase = base.take(USERNAME_MAX_LENGTH - 4)
        return sequence {
            yield("$trimmedBase${Random.nextInt(10, 100)}")
            yield("${trimmedBase}_${Random.nextInt(1, 1000)}")
            yield("$trimmedBase.${Random.nextInt(1, 100)}")
            yield("$trimmedBase${Random.nextInt(100, 1000)}")
            yield("$trimmedBase${Random.nextInt(1000, 10000)}")
        }
            .filterNot { userRepository.existsByUsername(it) }
            .take(MAX_SUGGESTIONS)
            .toList()
    }

    private fun User.toProfile() = UserProfile(
        userId = id,
        displayName = displayName,
        username = username,
        email = email,
        profilePhotoUrl = profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
        createdAt = createdAt,
    )
}
