package com.ember.backend.service

import com.ember.backend.dto.AuthResponse
import com.ember.backend.dto.ChangePasswordRequest
import com.ember.backend.dto.LoginRequest
import com.ember.backend.dto.RegisterRequest
import com.ember.backend.exception.EmailAlreadyRegisteredException
import com.ember.backend.exception.IncorrectPasswordException
import com.ember.backend.exception.InvalidCredentialsException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.exception.UsernameAlreadyTakenException
import com.ember.backend.model.User
import com.ember.backend.repository.UserRepository
import com.ember.backend.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val normalizedEmail = request.email.trim().lowercase()
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
            passwordHash = passwordEncoder.encode(request.password),
            displayName = sanitizeDisplayName(request.displayName),
        )
        userRepository.save(user)
        // Identifies the account by id only. Every one of these lines used to carry the email
        // address as well, which put a personal identifier into log output that is shipped to,
        // and retained by, whatever the host's log aggregator is — for the routine case of
        // someone simply signing up or signing in. The id is what's actually useful for tracing
        // and is already the key to look the rest up by.
        logger.info("Registered new user: userId={}", user.id)
        return AuthResponse(
            token = jwtService.issueAccessToken(user.id, user.email),
            userId = user.id,
            email = user.email,
            displayName = user.displayName,
            username = user.username,
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val normalizedIdentifier = request.identifier.trim().lowercase()
        // An "@" means an email was entered, anything else is treated as a username — the same
        // distinction registration already enforces (usernames are restricted to
        // letters/digits/underscore/period, see RegisterRequest, so they can never contain "@"
        // and be mistaken for one).
        val user = if (normalizedIdentifier.contains("@")) {
            userRepository.findByEmail(normalizedIdentifier)
        } else {
            userRepository.findByUsername(normalizedIdentifier)
        }
        // Always runs a real BCrypt comparison, even when no account matched — comparing against
        // a fixed dummy hash rather than short-circuiting on `user == null`. BCrypt is
        // deliberately slow (tens of ms), so skipping it made "no such account" measurably faster
        // than "wrong password," letting an attacker enumerate valid emails/usernames purely from
        // response timing, with no rate limiting standing in the way.
        val passwordMatches = passwordEncoder.matches(request.password, user?.passwordHash ?: DUMMY_PASSWORD_HASH)
        if (user == null || !passwordMatches) {
            // No identifier in the message — a failed-login log line is exactly where a mistyped
            // password ends up next to the address it was typed against, and on the "no such
            // account" path it would record an address that isn't even a user here.
            logger.info("Login failed")
            throw InvalidCredentialsException()
        }
        logger.info("User logged in: userId={}", user.id)
        return AuthResponse(
            token = jwtService.issueAccessToken(user.id, user.email),
            userId = user.id,
            email = user.email,
            displayName = user.displayName,
            username = user.username,
        )
    }

    /**
     * Changes the password and ends every other signed-in session for this account.
     *
     * This used to leave existing tokens alone, on the reasoning that a password change is just
     * another profile edit. It isn't: people change a password because they think someone else
     * has their account, and leaving that person signed in for the remaining seven days of their
     * token's life means the change accomplished nothing against the one threat it exists for.
     * Stamping `tokensValidFrom` revokes every token issued before now (see the V11 migration and
     * JwtAuthenticationFilter).
     *
     * That would include the caller's own token, so a replacement is issued and returned — the
     * device doing the change stays signed in, every other one is signed out. Without that, the
     * person changing their password would be bounced to the login screen by their own action.
     */
    @Transactional
    fun changePassword(userId: UUID, request: ChangePasswordRequest): AuthResponse {
        val user = userRepository.findById(userId).orElseThrow { ResourceNotFoundException("User not found") }
        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw IncorrectPasswordException()
        }
        user.passwordHash = passwordEncoder.encode(request.newPassword)
        // Truncated to whole seconds because that is the precision a JWT's `iat` claim is
        // serialized at. Left at nanosecond precision, the cutoff would land a fraction of a
        // second *after* the replacement token's own recorded issue time and reject it on the very
        // next request — signing out the device that just changed its password.
        user.tokensValidFrom = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        userRepository.save(user)
        logger.info("Password changed, other sessions revoked: userId={}", userId)

        // Issued after the cutoff above, so this one survives it.
        return AuthResponse(
            token = jwtService.issueAccessToken(user.id, user.email),
            userId = user.id,
            email = user.email,
            displayName = user.displayName,
            username = user.username,
        )
    }

    private companion object {

        // Not a real account's hash — just a well-formed BCrypt digest to compare against so a
        // nonexistent-user login takes the same amount of work as a real wrong-password check.
        const val DUMMY_PASSWORD_HASH = "\$2a\$12\$Eck.aaxqRvwDl.4Ll6uuBu.FfCLaTEvIA0UBU78kfZSiUJwqI7nVm"
    }
}
