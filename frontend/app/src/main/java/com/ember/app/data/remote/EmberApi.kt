package com.ember.app.data.remote

import com.ember.app.data.remote.dto.ActivityEventDto
import com.ember.app.data.remote.dto.ActivityLastSeenDto
import com.ember.app.data.remote.dto.AuthResponse
import com.ember.app.data.remote.dto.BlockedUserDto
import com.ember.app.data.remote.dto.DeviceTokenRequestDto
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.FriendAcceptBody
import com.ember.app.data.remote.dto.FriendRequestBody
import com.ember.app.data.remote.dto.FriendSearchResultDto
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.LoginRequest
import com.ember.app.data.remote.dto.MemoryPhotoDto
import com.ember.app.data.remote.dto.PageDto
import com.ember.app.data.remote.dto.PendingFriendRequestDto
import com.ember.app.data.remote.dto.PhotoUploadResponseDto
import com.ember.app.data.remote.dto.RegisterRequest
import com.ember.app.data.remote.dto.ReportUserRequestDto
import com.ember.app.data.remote.dto.SubscriptionStatusDto
import com.ember.app.data.remote.dto.UpdateProfileRequestDto
import com.ember.app.data.remote.dto.EmailAvailabilityDto
import com.ember.app.data.remote.dto.UsernameAvailabilityDto
import com.ember.app.data.remote.dto.UserProfileDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface EmberApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    /** Public counterpart to [checkUsernameAvailability] — used during registration, before an
     * account (and therefore a token) exists at all, so it can't go through the authenticated
     * `/users/me/...` route the way an existing user editing their own username does. */
    @GET("auth/username-availability")
    suspend fun checkUsernameAvailabilityPublic(@Query("username") username: String): Response<UsernameAvailabilityDto>

    /** Same public, pre-auth role as [checkUsernameAvailabilityPublic], for the email step — so a
     * taken address is caught while it's still being entered, rather than at the final register
     * call once a password, name and username have all already been filled in. */
    @GET("auth/email-availability")
    suspend fun checkEmailAvailabilityPublic(@Query("email") email: String): Response<EmailAvailabilityDto>

    @GET("photos/feed")
    suspend fun getFeed(@Query("refresh") refresh: Boolean = false): Response<List<FeedItem>>

    // start/end are ISO-8601 instant strings (see PhotoRepository.getMemoriesForRange) — one
    // calendar month's local-time boundaries, computed client-side and converted to UTC, so the
    // server never has to guess which timezone "this month" means.
    @GET("photos/memories")
    suspend fun getMemories(
        @Query("start") start: String,
        @Query("end") end: String,
    ): Response<List<MemoryPhotoDto>>

    @Multipart
    @POST("photos")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part,
        @Part recipientIds: List<MultipartBody.Part>,
    ): Response<PhotoUploadResponseDto>

    @POST("photos/{photoId}/seen")
    suspend fun markPhotoSeen(@Path("photoId") photoId: String): Response<Unit>

    @GET("friends")
    suspend fun getFriends(
        @Query("refresh") refresh: Boolean = false,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 30,
    ): Response<PageDto<FriendSummaryDto>>

    @GET("friends/pending")
    suspend fun getPendingFriendRequests(): Response<List<PendingFriendRequestDto>>

    @GET("friends/search")
    suspend fun searchFriends(@Query("q") query: String): Response<List<FriendSearchResultDto>>

    @POST("friends/request")
    suspend fun sendFriendRequest(@Body request: FriendRequestBody): Response<PendingFriendRequestDto>

    @POST("friends/accept")
    suspend fun acceptFriendRequest(@Body request: FriendAcceptBody): Response<FriendSummaryDto>

    @POST("friends/{friendshipId}/pin")
    suspend fun pinFriend(@Path("friendshipId") friendshipId: String): Response<FriendSummaryDto>

    @DELETE("friends/{friendshipId}/pin")
    suspend fun unpinFriend(@Path("friendshipId") friendshipId: String): Response<FriendSummaryDto>

    @DELETE("friends/{friendshipId}")
    suspend fun removeFriend(@Path("friendshipId") friendshipId: String): Response<Unit>

    @GET("activity")
    suspend fun getActivity(
        @Query("refresh") refresh: Boolean = false,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 30,
    ): Response<PageDto<ActivityEventDto>>

    @GET("activity/seen")
    suspend fun getActivityLastSeen(): Response<ActivityLastSeenDto>

    @POST("activity/seen")
    suspend fun markActivitySeen(): Response<ActivityLastSeenDto>

    @GET("users/me")
    suspend fun getMyProfile(): Response<UserProfileDto>

    @PATCH("users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequestDto): Response<UserProfileDto>

    @GET("users/me/username-availability")
    suspend fun checkUsernameAvailability(@Query("username") username: String): Response<UsernameAvailabilityDto>

    @Multipart
    @POST("users/me/photo")
    suspend fun uploadProfilePhoto(@Part file: MultipartBody.Part): Response<UserProfileDto>

    @GET("subscription/status")
    suspend fun getSubscriptionStatus(): Response<SubscriptionStatusDto>

    @POST("devices/register")
    suspend fun registerDevice(@Body request: DeviceTokenRequestDto): Response<Unit>

    @GET("users/blocked")
    suspend fun getBlockedUsers(): Response<List<BlockedUserDto>>

    @POST("users/{userId}/block")
    suspend fun blockUser(@Path("userId") userId: String): Response<Unit>

    @DELETE("users/{userId}/block")
    suspend fun unblockUser(@Path("userId") userId: String): Response<Unit>

    @POST("users/{userId}/report")
    suspend fun reportUser(
        @Path("userId") userId: String,
        @Body request: ReportUserRequestDto,
    ): Response<Unit>
}
