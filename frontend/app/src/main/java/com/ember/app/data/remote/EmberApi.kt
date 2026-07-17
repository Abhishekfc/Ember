package com.ember.app.data.remote

import com.ember.app.data.remote.dto.ActivityEventDto
import com.ember.app.data.remote.dto.AuthResponse
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.FriendRequestBody
import com.ember.app.data.remote.dto.FriendSearchResultDto
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.LoginRequest
import com.ember.app.data.remote.dto.PendingFriendRequestDto
import com.ember.app.data.remote.dto.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface EmberApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("photos/feed")
    suspend fun getFeed(): Response<List<FeedItem>>

    @GET("friends")
    suspend fun getFriends(): Response<List<FriendSummaryDto>>

    @GET("friends/pending")
    suspend fun getPendingFriendRequests(): Response<List<PendingFriendRequestDto>>

    @GET("friends/search")
    suspend fun searchFriends(@Query("q") query: String): Response<List<FriendSearchResultDto>>

    @POST("friends/request")
    suspend fun sendFriendRequest(@Body request: FriendRequestBody): Response<PendingFriendRequestDto>

    @GET("activity")
    suspend fun getActivity(): Response<List<ActivityEventDto>>
}
