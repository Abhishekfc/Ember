package com.ember.app.data

import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.AddPhotoRecipientsBody
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.MemoryPhotoDto
import com.ember.app.data.remote.dto.PhotoUploadResponseDto
import com.ember.app.data.remote.dto.SentPhotoDto
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

    // This repository is a process-wide singleton (see EmberApplication) that outlives any one
    // signed-in account, but feedCache's key (Unit) doesn't carry any account identity — without
    // clearing it on sign-out, signing into a different account within the TTL window could serve
    // that account the *previous* one's feed straight out of cache, on what looks like a perfectly
    // normal fresh fetch. Called from MainActivity's onSignOut, alongside the equivalent clear on
    // FriendRepository/ActivityRepository and LocalListCache.
    fun clearCache() {
        feedCache.invalidateAll()
    }

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

    // recipientIds can legitimately be empty now — a save-only upload from the camera's bookmark
    // button, no one selected to send to. save defaults to false so every existing send-only call
    // site doesn't need to change.
    suspend fun uploadPhoto(file: File, recipientIds: List<String>, save: Boolean = false): Result<PhotoUploadResponseDto> = safeCall {
        val filePart = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("image/jpeg".toMediaType()),
        )
        val recipientParts = recipientIds.map { MultipartBody.Part.createFormData("recipientIds", it) }
        val savePart = MultipartBody.Part.createFormData("save", save.toString())

        val response = api.uploadPhoto(filePart, recipientParts, savePart)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else if (response.code() == 401) {
            // Distinguished the same way getFeed does above — PendingSendWorker (which can run
            // long after the app that queued it is gone) needs to tell "session expired, stop
            // retrying" apart from every other, possibly-transient failure shape.
            Result.failure(UnauthorizedException())
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't send your photo (${response.code()})"
            Result.failure(Exception(message))
        }
    }

    suspend fun deletePhoto(photoId: String): Result<Unit> = safeCall {
        val response = api.deletePhoto(photoId)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't delete that photo (${response.code()})"
            Result.failure(Exception(message))
        }
    }

    /** This account's own outbox — recent, unsaved sends still within their unsend window. Not
     * cached: only fetched when the outbox screen is actually opened, and it's always meant to
     * reflect the current, real state (a photo aging out of its window, or being unsent from
     * another device, shouldn't keep showing from a stale snapshot). */
    suspend fun getSentPhotos(): Result<List<SentPhotoDto>> = safeCall {
        val response = api.getSentPhotos()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't load your sent photos (${response.code()})"
            Result.failure(Exception(message))
        }
    }

    /** Unsends a photo — the outbox's own delete, reusing the same endpoint Memories' delete
     * does (see PhotoService.delete on the backend for the 24h gate this is subject to that
     * Memories' own delete isn't). A photo past its unsend window fails here with a clear
     * server-provided message ("This photo can no longer be unsent") rather than a generic one,
     * same [ErrorResponse] parsing every other call in this class already does. */
    suspend fun unsendPhoto(photoId: String): Result<Unit> = deletePhoto(photoId)

    /** Reuses an already-uploaded photo instead of uploading the same file a second time — see
     * PendingSendWorker.AttachPhotoWorker, the only caller. Same 401 handling as [uploadPhoto]:
     * this can also run long after the app that queued it is gone. */
    suspend fun markPhotoSaved(photoId: String): Result<Unit> = safeCall {
        val response = api.markPhotoSaved(photoId)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else if (response.code() == 401) {
            Result.failure(UnauthorizedException())
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't save that photo (${response.code()})"
            Result.failure(Exception(message))
        }
    }

    suspend fun addPhotoRecipients(photoId: String, recipientIds: List<String>): Result<Unit> = safeCall {
        val response = api.addPhotoRecipients(photoId, AddPhotoRecipientsBody(recipientIds))
        if (response.isSuccessful) {
            Result.success(Unit)
        } else if (response.code() == 401) {
            Result.failure(UnauthorizedException())
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't send that photo (${response.code()})"
            Result.failure(Exception(message))
        }
    }
}
