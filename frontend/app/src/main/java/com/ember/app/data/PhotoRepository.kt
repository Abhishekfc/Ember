package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.PhotoUploadResponseDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class PhotoRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getFeed(): Result<List<FeedItem>> = safeCall {
        val response = api.getFeed()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't load your feed (${response.code()})"
            Result.failure(Exception(message))
        }
    }

    suspend fun uploadPhoto(file: File, recipientIds: List<String>): Result<PhotoUploadResponseDto> = safeCall {
        val filePart = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("image/jpeg".toMediaType()),
        )
        val recipientParts = recipientIds.map { MultipartBody.Part.createFormData("recipientIds", it) }

        val response = api.uploadPhoto(filePart, recipientParts)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't send your photo (${response.code()})"
            Result.failure(Exception(message))
        }
    }
}
