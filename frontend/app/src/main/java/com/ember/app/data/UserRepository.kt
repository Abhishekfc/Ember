package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.UpdateProfileRequestDto
import com.ember.app.data.remote.dto.UserProfileDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File

class UserRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getMyProfile(): Result<UserProfileDto> = handle(api.getMyProfile())

    suspend fun updateProfile(displayName: String, username: String): Result<UserProfileDto> =
        handle(api.updateProfile(UpdateProfileRequestDto(displayName = displayName, username = username)))

    suspend fun uploadProfilePhoto(file: File): Result<UserProfileDto> {
        val filePart = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("image/jpeg".toMediaType()),
        )
        return handle(api.uploadProfilePhoto(filePart))
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
