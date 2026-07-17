package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.FeedItem
import kotlinx.serialization.json.Json
import retrofit2.Response

class PhotoRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getFeed(): Result<List<FeedItem>> {
        val response = api.getFeed()
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't load your feed (${response.code()})"
            Result.failure(Exception(message))
        }
    }
}
