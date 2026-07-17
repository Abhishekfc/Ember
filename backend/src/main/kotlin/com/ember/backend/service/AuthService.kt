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
        val normalizedEmail = request.email.trim().lowercase()
        val user = userRepository.findByEmail(normalizedEmail)
        if (user == null) {
            logger.info("Login failed, no account for email: {}", normalizedEmail)
            throw InvalidCredentialsException()
        }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            logger.info("Login failed, wrong password: userId={} email={}", user.id, user.email)
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
}
