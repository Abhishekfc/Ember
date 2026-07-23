package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.ActivityEventDto
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.PageDto
import kotlinx.serialization.json.Json
import retrofit2.Response

class ActivityRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    // Matches the backend's own Redis TTL for this cache (CacheConfig.kt) — see
    // PhotoRepository.feedCache/FriendRepository.friendsCache for the same reasoning. Activity
    // has no other mutation method in this repository (friend/photo actions that affect it live
    // in FriendRepository/PhotoRepository), so there's no separate invalidation call site needed
    // here beyond the TTL itself.
    private val activityCache = TtlCache<Pair<Int, Int>, PageDto<ActivityEventDto>>(ttlMillis = 30_000)
    // Coalesces truly-concurrent getActivity() calls for the same (offset, limit) — the TTL
    // cache above only catches calls landing sequentially within its window.
    private val activitySingleFlight = SingleFlight<Pair<Int, Int>, Result<PageDto<ActivityEventDto>>>()

    suspend fun getActivity(forceRefresh: Boolean = false, offset: Int = 0, limit: Int = 30): Result<PageDto<ActivityEventDto>> {
        val cacheKey = offset to limit
        if (!forceRefresh) {
            activityCache.get(cacheKey)?.let { return Result.success(it) }
        }
        return activitySingleFlight.run(cacheKey) {
            safeCall {
                val response = api.getActivity(refresh = forceRefresh, offset = offset, limit = limit)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    Result.success(body)
                } else {
                    val message = response.errorBody()?.string()?.let {
                        runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
                    } ?: "Couldn't load activity (${response.code()})"
                    Result.failure(Exception(message))
                }
            }.onSuccess { activityCache.put(cacheKey, it) }
        }
    }
}
