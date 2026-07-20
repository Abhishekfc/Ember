package com.ember.app.data

import com.ember.app.data.local.TokenStore
import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.AuthResponse
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.LoginRequest
import com.ember.app.data.remote.dto.RegisterRequest
import kotlinx.serialization.json.Json
import retrofit2.Response

class AuthRepository(
    private val api: EmberApi,
    private val tokenStore: TokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun register(email: String, password: String, displayName: String, username: String): Result<AuthResponse> =
        safeCall { handle(api.register(RegisterRequest(email, password, displayName, username))) }

    suspend fun login(email: String, password: String): Result<AuthResponse> =
        safeCall { handle(api.login(LoginRequest(email, password))) }

    private suspend fun handle(response: Response<AuthResponse>): Result<AuthResponse> {
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            tokenStore.save(body.token)
            tokenStore.saveDisplayName(body.displayName)
            Result.success(body)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Something went wrong (${response.code()})"
            Result.failure(Exception(message))
        }
    }
}
