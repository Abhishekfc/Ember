package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.CreateRecipientListBody
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.FriendAcceptBody
import com.ember.app.data.remote.dto.FriendRequestBody
import com.ember.app.data.remote.dto.FriendSearchResultDto
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.PageDto
import com.ember.app.data.remote.dto.PendingFriendRequestDto
import com.ember.app.data.remote.dto.RecipientListDto
import kotlinx.serialization.json.Json
import retrofit2.Response

/** A generously high ceiling for "give me every friend," not a real pagination page size — just
 * far above any real user's friend count. Shared by every call site that needs the whole list at
 * once (Camera's recipient picker, Home's own friend-avatar photo lookup) rather than each one
 * hand-picking its own number. */
const val ALL_FRIENDS_LIMIT = 500

class FriendRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    // Matches the backend's own Redis TTL for this cache (CacheConfig.kt). Keyed by the exact
    // (offset, limit) pair — Camera's recipient picker (limit=500) and the Friends tab's own
    // paginated reads (limit=30) are genuinely different queries and deliberately stay separate
    // cache entries rather than being forced to share one.
    private val friendsCache = TtlCache<Pair<Int, Int>, PageDto<FriendSummaryDto>>(ttlMillis = 30_000)
    // Coalesces truly-concurrent getFriends() calls for the same (offset, limit) — e.g. Camera
    // and Friends both racing to fetch on a cold start — into one real network request; the TTL
    // cache above only catches calls landing sequentially within its window.
    private val friendsSingleFlight = SingleFlight<Pair<Int, Int>, Result<PageDto<FriendSummaryDto>>>()

    // See PhotoRepository.clearCache's doc comment — same reasoning, this repository outlives
    // any one signed-in account but friendsCache's keys carry no account identity of their own.
    fun clearCache() {
        friendsCache.invalidateAll()
    }

    suspend fun getFriends(forceRefresh: Boolean = false, offset: Int = 0, limit: Int = 30): Result<PageDto<FriendSummaryDto>> {
        val cacheKey = offset to limit
        if (!forceRefresh) {
            friendsCache.get(cacheKey)?.let { return Result.success(it) }
        }
        return friendsSingleFlight.run(cacheKey) {
            safeCall {
                handle(api.getFriends(refresh = forceRefresh, offset = offset, limit = limit)) { "Couldn't load your friends (${it})" }
            }.onSuccess { friendsCache.put(cacheKey, it) }
        }
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
    }.onSuccess { friendsCache.invalidateAll() }

    suspend fun setPinned(friendshipId: String, pinned: Boolean): Result<FriendSummaryDto> = safeCall {
        handle(if (pinned) api.pinFriend(friendshipId) else api.unpinFriend(friendshipId)) {
            "Couldn't update pin (${it})"
        }
    }.onSuccess { friendsCache.invalidateAll() }

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
    }.onSuccess { friendsCache.invalidateAll() }

    // Kept on the backend rather than only on-device (LocalListCache), specifically so a list
    // saved on one phone shows up after logging into the same account on another — see
    // RecipientPickerViewModel for the local-cache fast-path this is paired with.
    suspend fun getRecipientLists(): Result<List<RecipientListDto>> = safeCall {
        handle(api.getRecipientLists()) { "Couldn't load your saved lists (${it})" }
    }

    suspend fun createRecipientList(name: String, friendIds: List<String>): Result<RecipientListDto> = safeCall {
        handle(api.createRecipientList(CreateRecipientListBody(name = name, friendIds = friendIds))) {
            "Couldn't save that list (${it})"
        }
    }

    suspend fun deleteRecipientList(listId: String): Result<Unit> = safeCall {
        val response = api.deleteRecipientList(listId)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't delete that list (${response.code()})"
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
