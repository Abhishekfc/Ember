package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.FriendAcceptBody
import com.ember.app.data.remote.dto.FriendRequestBody
import com.ember.app.data.remote.dto.FriendSearchResultDto
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.PendingFriendRequestDto
import kotlinx.serialization.json.Json
import retrofit2.Response

class FriendRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getFriends(forceRefresh: Boolean = false): Result<List<FriendSummaryDto>> = safeCall {
        handle(api.getFriends(refresh = forceRefresh)) { "Couldn't load your friends (${it})" }
    }

    suspend fun getPendingRequests(): Result<List<PendingFriendRequestDto>> = safeCall {
        handle(api.getPendingFriendRequests()) { "Couldn't load friend requests (${it})" }
    }

    suspend fun searchUsers(query: String): Result<List<FriendSearchResultDto>> = safeCall {
        handle(api.searchFriends(query)) { "Search failed (${it})" }
    }

    suspend fun sendFriendRequest(targetUserId: String): Result<PendingFriendRequestDto> = safeCall {
        handle(api.sendFriendRequest(FriendRequestBody(targetUserId = targetUserId))) { "Couldn't send request (${it})" }
    }

    suspend fun acceptFriendRequest(friendshipId: String): Result<FriendSummaryDto> = safeCall {
        handle(api.acceptFriendRequest(FriendAcceptBody(friendshipId))) { "Couldn't accept request (${it})" }
    }

    suspend fun setPinned(friendshipId: String, pinned: Boolean): Result<FriendSummaryDto> = safeCall {
        handle(if (pinned) api.pinFriend(friendshipId) else api.unpinFriend(friendshipId)) {
            "Couldn't update pin (${it})"
        }
    }

    suspend fun removeFriend(friendshipId: String): Result<Unit> = safeCall {
        val response = api.removeFriend(friendshipId)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't remove friend (${response.code()})"
            Result.failure(Exception(message))
        }
    }

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
