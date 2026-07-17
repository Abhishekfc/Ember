package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.FriendRequestBody
import com.ember.app.data.remote.dto.FriendSearchResultDto
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.PendingFriendRequestDto
import kotlinx.serialization.json.Json
import retrofit2.Response

class FriendRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getFriends(): Result<List<FriendSummaryDto>> = handle(api.getFriends()) { "Couldn't load your friends (${it})" }

    suspend fun getPendingRequests(): Result<List<PendingFriendRequestDto>> =
        handle(api.getPendingFriendRequests()) { "Couldn't load friend requests (${it})" }

    suspend fun searchUsers(query: String): Result<List<FriendSearchResultDto>> =
        handle(api.searchFriends(query)) { "Search failed (${it})" }

    suspend fun sendFriendRequest(targetUserId: String): Result<PendingFriendRequestDto> =
        handle(api.sendFriendRequest(FriendRequestBody(targetUserId = targetUserId))) { "Couldn't send request (${it})" }

    private suspend fun <T> handle(response: Response<T>, defaultMessage: (Int) -> String): Result<T> {
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: defaultMessage(response.code())
            Result.failure(Exception(message))
        }
    }
}
