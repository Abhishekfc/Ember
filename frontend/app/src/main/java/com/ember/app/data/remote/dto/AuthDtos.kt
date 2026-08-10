package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val username: String,
)

@Serializable
data class LoginRequest(
    // Either the account's email or its username — the backend tells them apart by whether
    // this contains an "@" (see AuthService.login).
    val identifier: String,
    val password: String,
)

@Serializable
data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
    val email: String,
    val displayName: String,
    val username: String,
)
