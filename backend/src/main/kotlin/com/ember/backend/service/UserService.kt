package com.ember.backend.service

import com.ember.backend.dto.EmailAvailability
import com.ember.backend.dto.UpdateProfileRequest
import com.ember.backend.dto.UsernameAvailability
import com.ember.backend.dto.UsernameLoginLookup
import com.ember.backend.dto.UserProfile
import com.ember.backend.exception.InvalidFriendRequestException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.exception.UsernameAlreadyTakenException
import com.ember.backend.model.FriendshipStatus
import com.ember.backend.model.User
import com.ember.backend.repository.FriendshipRepository
import com.ember.backend.repository.PhotoRepository
import com.ember.backend.repository.UserRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
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
    private val friendshipRepository: FriendshipRepository,
    private val photoRepository: PhotoRepository,
    private val cacheManager: CacheManager,
    private val firebaseApp: FirebaseApp?,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getProfile(userId: UUID): UserProfile {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }
        return user.toProfile()
    }

    /** Not `@Transactional`: the only database work here is a single `save`, which carries its own
     * transaction, and wrapping the method held a pooled connection across the R2 upload — an
     * unbounded external call — for no atomicity benefit. Same reasoning as [deleteAccount]. */
    fun updateProfilePhoto(userId: UUID, file: MultipartFile): UserProfile {
        val contentType = file.contentType
        if (contentType == null || contentType !in ALLOWED_CONTENT_TYPES) {
            throw InvalidFriendRequestException("Unsupported content type: $contentType")
        }
        // Read once and reused — see PhotoService.upload for why repeated `file.bytes` calls are
        // worth avoiding.
        val uploadedBytes = file.bytes
        // Sniffs the actual bytes rather than trusting the client-declared Content-Type, which a
        // raw API call can set to anything regardless of what's actually in the file.
        val detectedType = ImageContentSniffer.detect(uploadedBytes)
        if (detectedType == null || detectedType !in ALLOWED_CONTENT_TYPES) {
            throw InvalidFriendRequestException("File content doesn't match a supported image type")
        }
        // A profile photo went through no size normalization at all — the raw upload was stored
        // and then re-fetched by every screen showing that person's avatar. This runs it through
        // the same compression (and the same decompression-bomb guard) every photo upload gets.
        val compressed = PhotoCompressionService.compress(uploadedBytes, detectedType)

        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val extension = when (compressed.contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val storageKey = "profile-photos/$userId/${UUID.randomUUID()}.$extension"
        r2StorageService.upload(storageKey, compressed.contentType, compressed.bytes)

        val previousKey = user.profilePhotoStorageKey
        user.profilePhotoStorageKey = storageKey
        userRepository.save(user)
        logger.info("Profile photo updated: userId={}", userId)

        // Only once the new key is committed. The old object is now unreachable — nothing points
        // at it any more — so without this every profile-photo change leaves a file in R2 that no
        // code path can ever reach or delete again, accumulating for the life of the account.
        // Deliberately *after* the save rather than before it: deleting first would, if the save
        // then failed, leave the row still pointing at a key whose object had already been
        // removed — a permanently broken avatar. Best-effort by design (see R2StorageService.delete).
        if (previousKey != null && previousKey != storageKey) {
            r2StorageService.delete(previousKey)
        }

        return user.toProfile()
    }

    @Transactional
    fun updateProfile(userId: UUID, request: UpdateProfileRequest): UserProfile {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        // Same normalization registration applies — see sanitizeDisplayName. Without it here, a
        // name rejected at sign-up could simply be set afterwards through this endpoint instead.
        request.displayName?.let { user.displayName = sanitizeDisplayName(it) }

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

    /** Resolves a username typed into the login screen back to the email Firebase actually needs
     * to sign in with — same trim+lowercase normalization [AuthService.register] stores usernames
     * with, so this matches exactly what a real account was created with. Returns a null email for
     * no match rather than throwing or 404ing, so the controller (and rate limiting) here looks
     * identical whether the username exists or not — the one-line difference between "doesn't
     * exist" and "wrong password" is exactly what [checkEmailAvailabilityPublic] already avoids
     * leaking for email, and a username deserves the same treatment. */
    fun resolveUsernameForLogin(candidate: String): UsernameLoginLookup {
        val normalized = candidate.trim().lowercase()
        if (normalized.isEmpty()) return UsernameLoginLookup(email = null)
        return UsernameLoginLookup(email = userRepository.findByUsername(normalized)?.email)
    }

    /**
     * Permanently deletes an account. Every other table with a foreign key to `users` (photos,
     * photo_recipients, friendships, device_tokens, blocked_users, subscriptions, user_reports,
     * photo_reactions) is `ON DELETE CASCADE`, so deleting the row itself is enough to clean up
     * the database side — the two things that need doing by hand first are the parts a DB
     * cascade can't reach:
     *
     * 1. The actual files in R2 (a cascaded `photos` row deletes the *row*, not the object it
     *    points to — nothing in Postgres knows R2 exists).
     * 2. Every friend's own cached friends/feed/activity list, which would otherwise keep
     *    showing this account until each cache's TTL happens to expire on its own, the same
     *    staleness [removeFriend] already guards against for a single unfriend.
     */
    /**
     * Deliberately **not** `@Transactional`, and the storage cleanup deliberately runs *after* the
     * row is gone rather than before it.
     *
     * Previously both happened inside one transaction: a pooled database connection was held open
     * across one sequential R2 network round-trip per photo the account had ever sent. For an
     * account with any real history that is minutes of a connection doing nothing, out of a pool
     * of ten — a handful of concurrent deletions could exhaust it and stall every unrelated
     * request in the app. It also meant a storage failure rolled back the deletion itself, which
     * inverts the priority this method's own comment states: the account row disappearing is what
     * matters, an orphaned R2 object with nothing pointing at it is the smaller problem.
     *
     * `userRepository.delete` carries its own transaction (Spring Data), so the row deletion and
     * its cascades are still atomic.
     */
    fun deleteAccount(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { ResourceNotFoundException("User not found") }

        val friendIds = friendshipRepository.findAllForUserWithStatus(userId, FriendshipStatus.ACCEPTED)
            .map { if (it.requester.id == userId) it.addressee.id else it.requester.id }
        friendIds.forEach { friendId ->
            cacheManager.getCache("friends")?.evict(friendId.toString())
            cacheManager.getCache("feed")?.evict(friendId.toString())
            cacheManager.getCache("activity")?.evict(friendId.toString())
        }

        // Collected before the delete (the rows are about to cascade away), used after it.
        val storageKeys = photoRepository.findStorageKeysBySenderId(userId) +
            listOfNotNull(user.profilePhotoStorageKey)

        // Collected before the delete for the same reason as storageKeys above — the row (and
        // this column) is about to be gone.
        val firebaseUid = user.firebaseUid

        userRepository.delete(user)
        logger.info("Account deleted: userId={}", userId)

        storageKeys.forEach { r2StorageService.delete(it) }

        // Without this, "delete my account" only ever removed this app's own data — the actual
        // Firebase Authentication identity (email + password) lived on forever, so the same
        // credentials could still sign in afterward, landing on a bare Firebase identity with no
        // Emigo profile (the same NeedsProfile state a genuinely interrupted sign-up produces).
        // That's a real gap against what "delete my account" promises in the privacy policy, not
        // just a cosmetic leftover. Best-effort and swallowed the same way R2 cleanup above is —
        // this runs after the row we actually care about is already gone, so a Firebase API
        // hiccup here must never make an otherwise-successful account deletion look like it
        // failed. Null only during the one-time migration window for an account never imported
        // into Firebase at all, in which case there's nothing here to clean up.
        if (firebaseUid != null) {
            val app = firebaseApp
            if (app != null) {
                runCatching { FirebaseAuth.getInstance(app).deleteUser(firebaseUid) }
                    .onFailure { logger.warn("Failed to delete Firebase identity during account deletion: firebaseUid={}", firebaseUid, it) }
            }
        }
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
        emailVerificationRequired = emailVerificationRequired,
    )
}
