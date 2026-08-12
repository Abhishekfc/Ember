package com.emigo.app.data

import com.emigo.app.data.local.TokenStore
import com.emigo.app.data.remote.EmberApi
import com.emigo.app.data.remote.dto.AuthResponse
import com.emigo.app.data.remote.dto.DeviceTokenRequestDto
import com.emigo.app.data.remote.dto.ErrorResponse
import com.emigo.app.data.remote.dto.LoginRequest
import com.emigo.app.data.remote.dto.RegisterRequest
import com.emigo.app.data.remote.dto.EmailAvailabilityDto
import com.emigo.app.data.remote.dto.UsernameAvailabilityDto
import kotlinx.serialization.json.Json
import retrofit2.Response

class AuthRepository(
    private val api: EmberApi,
    private val tokenStore: TokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun register(email: String, password: String, displayName: String, username: String): Result<AuthResponse> =
        safeCall { handle(api.register(RegisterRequest(email, password, displayName, username))) }

    /** [identifier] can be either the account's email or its username — see LoginRequest. */
    suspend fun login(identifier: String, password: String): Result<AuthResponse> =
        safeCall { handle(api.login(LoginRequest(identifier, password))) }

    /** Used while picking a username during registration, before an account/token exists — see
     * EmberApi.checkUsernameAvailabilityPublic for why this can't go through UserRepository's
     * authenticated equivalent. */
    suspend fun checkUsernameAvailability(username: String): Result<UsernameAvailabilityDto> = safeCall {
        val response = api.checkUsernameAvailabilityPublic(username)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(Exception("Couldn't check that username"))
        }
    }

    /** Public, pre-auth email check used by the registration email step. */
    suspend fun checkEmailAvailability(email: String): Result<EmailAvailabilityDto> = safeCall {
        val response = api.checkEmailAvailabilityPublic(email)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(Exception("Couldn't check that email"))
        }
    }

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

    /**
     * Detaches this device from the signed-out account's push list.
     *
     * Without it, signing out left the device's FCM token still attached server-side, so the
     * account that was just signed out of kept pushing "<friend> sent you a photo" — with the
     * sender's real name in the notification shade — to a phone now sitting on the login screen,
     * or in someone else's hands. Nothing on the device could suppress those: the token is what
     * the server sends to, and clearing the local JWT has no effect on it.
     *
     * Must run *before* the token store is cleared, since this call is itself authenticated. Best
     * effort — if it fails (offline sign-out being the obvious case) the account simply keeps the
     * stale token until FCM reports it dead or the next account to sign in on this device
     * reclaims it, which is exactly the old behaviour, so a failure never blocks signing out.
     */
    suspend fun unregisterDeviceToken(fcmToken: String): Result<Unit> = safeCall {
        val response = api.unregisterDevice(DeviceTokenRequestDto(fcmToken))
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Couldn't unregister device (${response.code()})"))
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
