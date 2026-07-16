package com.ember.backend.service

import com.ember.backend.dto.AuthResponse
import com.ember.backend.dto.LoginRequest
import com.ember.backend.dto.RegisterRequest
import com.ember.backend.exception.EmailAlreadyRegisteredException
import com.ember.backend.exception.InvalidCredentialsException
import com.ember.backend.model.User
import com.ember.backend.repository.UserRepository
import com.ember.backend.security.JwtService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val normalizedEmail = request.email.trim().lowercase()
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw EmailAlreadyRegisteredException()
        }
        val user = User(
            email = normalizedEmail,
            passwordHash = passwordEncoder.encode(request.password),
            displayName = request.displayName.trim(),
        )
        userRepository.save(user)
        return AuthResponse(
            token = jwtService.issueAccessToken(user.id, user.email),
            userId = user.id,
            email = user.email,
            displayName = user.displayName,
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val normalizedEmail = request.email.trim().lowercase()
        val user = userRepository.findByEmail(normalizedEmail) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }
        return AuthResponse(
            token = jwtService.issueAccessToken(user.id, user.email),
            userId = user.id,
            email = user.email,
            displayName = user.displayName,
        )
    }
}
