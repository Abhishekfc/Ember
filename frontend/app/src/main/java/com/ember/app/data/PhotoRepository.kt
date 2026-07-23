package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.MemoryPhotoDto
import com.ember.app.data.remote.dto.PhotoUploadResponseDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.Instant

class PhotoRepository(private val api: EmberApi) {
    private val json = Json { ignoreUnknownKeys = true }

    // Matches the backend's own Redis TTL for the feed cache (CacheConfig.kt) — no value in
    // the client claiming fresher data than the server would even hand back within that
    // window. Only ever covers the plain "current feed" read; forceRefresh (pull-to-refresh)
    // always bypasses the read, same as it bypasses the backend's own cache, but still
    // repopulates below so it doesn't leave this cold for the next normal read.
    private val feedCache = TtlCache<Unit, List<FeedItem>>(ttlMillis = 30_000)
    // Coalesces truly-concurrent getFeed() calls (e.g. two screens/callers landing at the same
    // instant, before feedCache has anything to serve yet) into one real network request — the
    // TTL cache above only catches calls that land *sequentially* within its window.
    private val feedSingleFlight = SingleFlight<Unit, Result<List<FeedItem>>>()

    suspend fun getFeed(forceRefresh: Boolean = false): Result<List<FeedItem>> {
        if (!forceRefresh) {
            feedCache.get(Unit)?.let { return Result.success(it) }
        }
        return feedSingleFlight.run(Unit) {
            safeCall {
                val response = api.getFeed(refresh = forceRefresh)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    Result.success(body)
                } else if (response.code() == 401) {
                    // Distinguished from every other failure shape so a caller with no
                    // NetworkModule.sessionExpired collector of its own (see WidgetUpdateWorker,
                    // which can run when the app process isn't alive) can still detect "this
                    // session is dead" and clear the token itself.
                    Result.failure(UnauthorizedException())
                } else {
                    val message = response.errorBody()?.string()?.let {
                        runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
                    } ?: "Couldn't load your feed (${response.code()})"
                    Result.failure(Exception(message))
                }
            }.onSuccess { feedCache.put(Unit, it) }
        }
    }

    /** Fetches every photo this user has sent within [start, end) (one calendar month's
     * local-time boundaries, computed by the caller and passed as UTC instants) — see
     * EmberApi.getMemories and PhotoRepository.kt (backend) for why this replaced the old
     * offset/limit pagination, which was silently capped at each user's most recent 200 photos
     * total. */
    suspend fun getMemoriesForRange(start: Instant, end: Instant): Result<List<MemoryPhotoDto>> = safeCall {
        val response = api.getMemories(start = start.toString(), end = end.toString())
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't load your memories (${response.code()})"
            Result.failure(Exception(message))
        }
    }

    /** Fire-and-forget from the caller's side (see HomeViewModel.markPhotoSeen, which updates
     * local state optimistically and doesn't wait on this) — a failure here just means the next
     * real feed fetch will show this photo as unseen again, not a user-facing error. */
    suspend fun markPhotoSeen(photoId: String): Result<Unit> = safeCall {
        val response = api.markPhotoSeen(photoId)
        if (response.isSuccessful) {
            // Without this, a getFeed() call landing within the next 30s (e.g. a
            // pull-to-refresh right after swiping) could serve back the pre-mark-seen
            // snapshot cached above, regressing this exact photo back to "unseen".
            feedCache.invalidateAll()
            Result.success(Unit)
        } else {
            Result.failure(Exception("Couldn't mark photo seen (${response.code()})"))
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
