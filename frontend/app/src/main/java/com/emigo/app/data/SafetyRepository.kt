package com.emigo.app.data

import com.emigo.app.data.remote.EmberApi
import com.emigo.app.data.remote.dto.BlockedUserDto
import com.emigo.app.data.remote.dto.ErrorResponse
import com.emigo.app.data.remote.dto.ReportReason
import com.emigo.app.data.remote.dto.ReportUserRequestDto
import kotlinx.serialization.json.Json

/** Blocking and reporting — a distinct concern from friend management (FriendRepository), same
 * split the backend itself makes (UserSafetyController vs FriendsController/FriendService). No
 * local caching here unlike FriendRepository/ActivityRepository — the blocked-users list is only
 * ever read on demand (opening Settings' own screen for it), not hit repeatedly enough on a hot
 * path to need one. */
class SafetyRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getBlockedUsers(): Result<List<BlockedUserDto>> = safeCall {
        val response = api.getBlockedUsers()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(Exception(errorMessage(response.errorBody()?.string(), "Couldn't load blocked users (${response.code()})")))
        }
    }

    suspend fun blockUser(userId: String): Result<Unit> = safeCall {
        val response = api.blockUser(userId)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(errorMessage(response.errorBody()?.string(), "Couldn't block this person (${response.code()})")))
        }
    }

    suspend fun unblockUser(userId: String): Result<Unit> = safeCall {
        val response = api.unblockUser(userId)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(errorMessage(response.errorBody()?.string(), "Couldn't unblock (${response.code()})")))
        }
    }

    suspend fun reportUser(userId: String, reason: ReportReason, details: String? = null): Result<Unit> = safeCall {
        val response = api.reportUser(userId, ReportUserRequestDto(reason = reason, details = details))
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(errorMessage(response.errorBody()?.string(), "Couldn't submit report (${response.code()})")))
        }
    }

    private fun errorMessage(body: String?, fallback: String): String =
        body?.let { runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull() } ?: fallback
}
