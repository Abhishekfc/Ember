package com.ember.app.data

import com.ember.app.data.local.TokenStore
import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.AuthResponse
import com.ember.app.data.remote.dto.DeviceTokenRequestDto
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

    /** Called whenever a fresh FCM token becomes available (see EmberFirebaseMessagingService)
     * and once whenever a session becomes authenticated (fresh login, or an already-valid
     * session found at cold start — see MainActivity), since either moment can be the first time
     * a token and a signed-in user actually coexist. Fire-and-forget from the caller's side: a
     * failure here just means this device won't receive pushes until the next successful
     * registration attempt, not a user-facing error. */
    suspend fun registerDeviceToken(fcmToken: String): Result<Unit> = safeCall {
        val response = api.registerDevice(DeviceTokenRequestDto(fcmToken))
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Couldn't register device (${response.code()})"))
        }
    }

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
