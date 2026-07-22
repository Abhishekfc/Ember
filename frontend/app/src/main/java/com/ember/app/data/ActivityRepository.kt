package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.ActivityEventDto
import com.ember.app.data.remote.dto.ErrorResponse
import kotlinx.serialization.json.Json
import retrofit2.Response

class ActivityRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getActivity(forceRefresh: Boolean = false): Result<List<ActivityEventDto>> = safeCall {
        val response = api.getActivity(refresh = forceRefresh)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't load activity (${response.code()})"
            Result.failure(Exception(message))
        }
    }
}
