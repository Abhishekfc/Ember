package com.ember.app.data.remote

import android.content.Context
import com.ember.app.data.local.TokenStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Points at the device's own "localhost". For a USB-connected physical device this relies on
 * `adb reverse tcp:8080 tcp:8080`, which tunnels that back to the host machine's localhost:8080.
 * An emulator would instead need 10.0.2.2 (its special host-loopback alias, no adb reverse
 * required); production needs the real deployed backend URL.
 */
private const val BASE_URL = "http://localhost:8080/"

private val json = Json { ignoreUnknownKeys = true }

/** Manual, hand-rolled DI: a single shared Retrofit client for the whole app. */
class NetworkModule(context: Context) {

    val tokenStore = TokenStore(context.applicationContext)

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when an authenticated request comes back 401 — the token was rejected (expired or
     * revoked), as opposed to a login/register attempt with the wrong password, which also
     * returns 401 but never carried a token in the first place. MainActivity collects this to
     * sign the user out back to the login screen instead of leaving the app stuck on a
     * permanently failing feed/friends load. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val token = runBlocking { tokenStore.currentToken() }
        val request = if (token != null) {
            chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    // Must run after authInterceptor so chain.request() here reflects the Authorization header
    // it just added — that's how we tell "token rejected" apart from "no token to begin with".
    private val sessionExpiryInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401 && request.header("Authorization") != null) {
            _sessionExpired.tryEmit(Unit)
        }
        response
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(sessionExpiryInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: EmberApi = retrofit.create(EmberApi::class.java)
}
