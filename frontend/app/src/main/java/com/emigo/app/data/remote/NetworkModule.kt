package com.emigo.app.data.remote

import android.content.Context
import com.emigo.app.BuildConfig
import com.emigo.app.data.local.TokenStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
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

    /** Emits when an authenticated request comes back 401 — the Firebase identity making the
     * request has no matching Emigo profile (or Firebase itself rejected it, e.g. the account was
     * deleted server-side). MainActivity collects this to sign the user out back to the login
     * screen instead of leaving the app stuck on a permanently failing feed/friends load. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    // No token is read from local storage any more — every request asks Firebase's own SDK for
    // the current ID token, which the SDK silently refreshes on its own schedule (tokens last an
    // hour) and persists across restarts in its own storage. `getIdToken(false)` uses whatever
    // Firebase already has cached rather than forcing a network refresh on every single request;
    // the SDK still refreshes proactively in the background before expiry, so this is very rarely
    // stale, and the one case it would be (a token revoked server-side) is exactly what
    // sessionExpiryInterceptor below exists to catch.
    private val authInterceptor = okhttp3.Interceptor { chain ->
        val token = runBlocking {
            runCatching { FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token }.getOrNull()
        }
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
    // Two endpoints are excluded, both because a 401 from them is expected and already handled by
    // their own caller, rather than evidence the session is dead:
    //
    // - devices/unregister is the *first* thing sign-out does (see MainActivity.onSignOut), so
    //   when sign-out was itself triggered by a 401 the token it carries is already dead and this
    //   call 401s too. Left unexcluded, that second 401 emits sessionExpired again, which runs
    //   onSignOut again, which calls this again — an endless sign-out loop firing a request every
    //   round. Its result is irrelevant to session state regardless.
    //
    // - GET /users/me is how AuthRepository.checkExistingProfile *asks* whether a signed-in
    //   Firebase identity has an Emigo profile yet; a 401 there is the documented answer "not
    //   yet", which it turns into SignInOutcome.NeedsProfile so sign-in can continue into choosing
    //   a username. Left unexcluded, that same 401 also fired sessionExpired, and the resulting
    //   sign-out beat the NeedsProfile routing — so signing in with a real Firebase identity that
    //   has no profile row (an interrupted sign-up, or any account that exists in Firebase but not
    //   in whichever database the app is currently pointed at, since Firebase is shared between
    //   local and production while the databases are not) just bounced straight back to the login
    //   screen, making NeedsProfile effectively unreachable. Excluding it costs nothing: a
    //   genuinely dead session still 401s on the feed/friends/activity calls that follow, and
    //   those still sign the user out.
    private val sessionExpiryInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val path = request.url.encodedPath
        val isSelfHandled401 = path.endsWith("/devices/unregister") ||
            (request.method == "GET" && path.endsWith("/users/me"))
        if (response.code == 401 && request.header("Authorization") != null && !isSelfHandled401) {
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
