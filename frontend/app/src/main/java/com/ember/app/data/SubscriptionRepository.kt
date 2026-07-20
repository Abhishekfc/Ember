package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.SubscriptionStatusDto
import kotlinx.serialization.json.Json

class SubscriptionRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStatus(): Result<SubscriptionStatusDto> = safeCall {
        val response = api.getSubscriptionStatus()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't check subscription status (${response.code()})"
            Result.failure(Exception(message))
        }
    }
}
