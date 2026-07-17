package com.ember.app.data.remote

import android.content.Context
import com.ember.app.data.local.TokenStore
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

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val token = runBlocking { tokenStore.currentToken() }
        val request = if (token != null) {
            chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: EmberApi = retrofit.create(EmberApi::class.java)
}
