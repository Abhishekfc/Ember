package com.ember.backend.service

import com.ember.backend.dto.AuthResponse
import com.ember.backend.dto.LoginRequest
import com.ember.backend.dto.RegisterRequest
import com.ember.backend.exception.EmailAlreadyRegisteredException
import com.ember.backend.exception.InvalidCredentialsException
import com.ember.backend.exception.UsernameAlreadyTakenException
import com.ember.backend.model.User
import com.ember.backend.repository.UserRepository
import com.ember.backend.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
            logger.info("Registration rejected, email already exists: {}", normalizedEmail)
            throw EmailAlreadyRegisteredException()
        }
        val normalizedUsername = request.username.trim().lowercase()
        if (userRepository.existsByUsername(normalizedUsername)) {
            logger.info("Registration rejected, username already taken: {}", normalizedUsername)
            throw UsernameAlreadyTakenException()
        }
        val user = User(
            email = normalizedEmail,
            username = normalizedUsername,
            passwordHash = passwordEncoder.encode(request.password),
            displayName = request.displayName.trim(),
        )
        userRepository.save(user)
        logger.info("Registered new user: userId={} email={} username={}", user.id, user.email, user.username)
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
            logger.info("Login failed for identifier: {}", normalizedIdentifier)
            throw InvalidCredentialsException()
        }
        logger.info("User logged in: userId={} email={}", user.id, user.email)
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
