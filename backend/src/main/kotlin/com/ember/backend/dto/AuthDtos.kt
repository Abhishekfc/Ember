package com.ember.backend.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class RegisterRequest(
    @field:Email @field:NotBlank val email: String,
    @field:Size(min = 8, max = 72) val password: String,
    @field:NotBlank @field:Size(max = 100) val displayName: String,
    @field:NotBlank @field:Size(min = 3, max = 30)
    @field:Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "Username can only contain letters, numbers, underscores, and periods")
    val username: String,
)

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

data class AuthResponse(
    val token: String,
    val userId: UUID,
    val email: String,
    val displayName: String,
    val username: String,
)
