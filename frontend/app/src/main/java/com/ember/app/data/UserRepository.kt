package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.ChangePasswordRequestDto
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.UpdateProfileRequestDto
import com.ember.app.data.remote.dto.UsernameAvailabilityDto
import com.ember.app.data.remote.dto.UserProfileDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File

class UserRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getMyProfile(): Result<UserProfileDto> = safeCall { handle(api.getMyProfile()) }

    // Both params are optional so a single-field edit (the "tap a row, change just that one
    // thing" popup flow) doesn't need to resend the other field's current value.
    suspend fun updateProfile(displayName: String? = null, username: String? = null): Result<UserProfileDto> = safeCall {
        handle(api.updateProfile(UpdateProfileRequestDto(displayName = displayName, username = username)))
    }

    suspend fun checkUsernameAvailability(username: String): Result<UsernameAvailabilityDto> = safeCall {
        val response = api.checkUsernameAvailability(username)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(Exception("Couldn't check that username"))
        }
    }

    suspend fun uploadProfilePhoto(file: File): Result<UserProfileDto> = safeCall {
        val filePart = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("image/jpeg".toMediaType()),
        )
        handle(api.uploadProfilePhoto(filePart))
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = safeCall {
        val response = api.changePassword(ChangePasswordRequestDto(currentPassword = currentPassword, newPassword = newPassword))
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't change your password (${response.code()})"
            Result.failure(Exception(message))
        }
    }

    /** Irreversible — the backend has already deleted the account and everything it owns by the
     * time this returns successfully. The caller is responsible for the same local sign-out
     * cleanup (token, caches) a normal sign-out does, since there's no account left for any of
     * that cached state to belong to either. */
    suspend fun deleteAccount(): Result<Unit> = safeCall {
        val response = api.deleteAccount()
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't delete your account (${response.code()})"
            Result.failure(Exception(message))
        }
    }

    private fun handle(response: Response<UserProfileDto>): Result<UserProfileDto> {
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't update your profile (${response.code()})"
            Result.failure(Exception(message))
        }
    }
}
