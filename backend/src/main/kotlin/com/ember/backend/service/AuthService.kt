package com.ember.backend.service

import com.ember.backend.dto.CompleteProfileRequest
import com.ember.backend.dto.UserProfile
import com.ember.backend.exception.EmailAlreadyRegisteredException
import com.ember.backend.exception.UsernameAlreadyTakenException
import com.ember.backend.model.User
import com.ember.backend.repository.UserRepository
import com.ember.backend.security.VerifiedFirebaseToken
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(private val userRepository: UserRepository) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Turns an already-verified Firebase identity into a real Emigo account, the moment someone
     * finishes choosing a username and display name after signing up. [firebaseToken] is never
     * client-supplied data — it's the result of [com.ember.backend.security.FirebaseTokenVerifier]
     * already having checked the token's signature, so [firebaseToken.email] is genuinely the
     * address behind this identity, not merely something the caller typed.
     *
     * Idempotent by [VerifiedFirebaseToken.uid]: a retry after a network blip (the profile was
     * actually created, but the client never saw the response) returns the existing profile
     * instead of failing on the now-duplicate `firebaseUid` — the same request replayed twice must
     * not read as two different accounts fighting over one identity.
     */
    @Transactional
    fun completeProfile(firebaseToken: VerifiedFirebaseToken, request: CompleteProfileRequest): UserProfile {
        userRepository.findByFirebaseUid(firebaseToken.uid)?.let { return it.toProfile() }

        // Deliberately lenient: an unverified email/password identity can still complete a
        // profile and use the app immediately, the same way most consumer apps behave (Instagram,
        // WhatsApp, Snapchat all let you in and remind you later, rather than blocking on a link
        // click before you've even seen the product). Firebase still sends the verification email
        // (see the client's own sign-up flow) and still records emailVerified on the token going
        // forward — nothing here is lost, this endpoint just stops treating it as a gate.
        val normalizedEmail = firebaseToken.email.trim().lowercase()
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw EmailAlreadyRegisteredException()
        }
        val normalizedUsername = request.username.trim().lowercase()
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw UsernameAlreadyTakenException()
        }

        val user = User(
            email = normalizedEmail,
            username = normalizedUsername,
            firebaseUid = firebaseToken.uid,
            displayName = sanitizeDisplayName(request.displayName),
        )
        userRepository.save(user)
        logger.info("Profile completed for Firebase-authenticated user: userId={}", user.id)
        return user.toProfile()
    }

    private fun User.toProfile() = UserProfile(
        userId = id,
        displayName = displayName,
        username = username,
        email = email,
        profilePhotoUrl = null,
        createdAt = createdAt,
    )
}
