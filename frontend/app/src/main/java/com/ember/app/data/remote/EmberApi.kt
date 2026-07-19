package com.ember.app.data.remote

import com.ember.app.data.remote.dto.ActivityEventDto
import com.ember.app.data.remote.dto.AuthResponse
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.FriendRequestBody
import com.ember.app.data.remote.dto.FriendSearchResultDto
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.LoginRequest
import com.ember.app.data.remote.dto.PendingFriendRequestDto
import com.ember.app.data.remote.dto.PhotoUploadResponseDto
import com.ember.app.data.remote.dto.RegisterRequest
import com.ember.app.data.remote.dto.UpdateProfileRequestDto
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

    @GET("photos/feed")
    suspend fun getFeed(): Response<List<FeedItem>>

    @Multipart
    @POST("photos")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part,
        @Part recipientIds: List<MultipartBody.Part>,
    ): Response<PhotoUploadResponseDto>

    @GET("friends")
    suspend fun getFriends(): Response<List<FriendSummaryDto>>

    @GET("friends/pending")
    suspend fun getPendingFriendRequests(): Response<List<PendingFriendRequestDto>>

    @GET("friends/search")
    suspend fun searchFriends(@Query("q") query: String): Response<List<FriendSearchResultDto>>

    @POST("friends/request")
    suspend fun sendFriendRequest(@Body request: FriendRequestBody): Response<PendingFriendRequestDto>

    @POST("friends/{friendshipId}/pin")
    suspend fun pinFriend(@Path("friendshipId") friendshipId: String): Response<FriendSummaryDto>

    @DELETE("friends/{friendshipId}/pin")
    suspend fun unpinFriend(@Path("friendshipId") friendshipId: String): Response<FriendSummaryDto>

    @DELETE("friends/{friendshipId}")
    suspend fun removeFriend(@Path("friendshipId") friendshipId: String): Response<Unit>

    @GET("activity")
    suspend fun getActivity(): Response<List<ActivityEventDto>>

    @GET("users/me")
    suspend fun getMyProfile(): Response<UserProfileDto>

    @PATCH("users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequestDto): Response<UserProfileDto>

    @Multipart
    @POST("users/me/photo")
    suspend fun uploadProfilePhoto(@Part file: MultipartBody.Part): Response<UserProfileDto>
}
