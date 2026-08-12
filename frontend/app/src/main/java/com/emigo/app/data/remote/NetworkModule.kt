package com.emigo.app.data.remote

import android.content.Context
import com.emigo.app.BuildConfig
import com.emigo.app.data.local.TokenStore
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
import java.util.concurrent.TimeUnit

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
    //
    // users/me/password is excluded even though it's authenticated (always carries a token) —
    // it has its own legitimate 401 (IncorrectPasswordException, wrong *current* password), which
    // isn't a rejected/expired session at all. Without this exclusion, typing the current password
    // wrong signed the whole app out instead of showing an inline error in the dialog, since this
    // interceptor couldn't tell that 401 apart from a real token rejection.
    private val sessionExpiryInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val isChangePassword = request.url.encodedPath.endsWith("/users/me/password")
        if (response.code == 401 && request.header("Authorization") != null && !isChangePassword) {
            _sessionExpired.tryEmit(Unit)
        }
        response
    }

    private val okHttpClient = OkHttpClient.Builder()
        // OkHttp's default is 10s connect/read/write — too tight for a photo upload: the backend
        // does an R2 object-storage write, push-notification fanout, and cache eviction all
        // synchronously before it responds (see PhotoService.upload), which can occasionally run
        // past 10s on a dev machine tunneled through adb reverse. When that timeout fires
        // client-side, the upload has usually already landed server-side — the send just reports
        // a failure for a photo that shows up fine on the next refresh.
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(sessionExpiryInterceptor)
        .apply {
            // Ran unconditionally in every build variant before, including release — full
            // request/response lines (URLs, query strings, status, timing) shipped to Logcat in
            // production with no way to disable it short of editing code. Gating it here also
            // means a future bump to a more verbose level (HEADERS/BODY, which would include the
            // bearer token and full response payloads) can only ever affect debug builds.
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: EmberApi = retrofit.create(EmberApi::class.java)
}
