package com.emigo.app.invite

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Asks Google Play which link this install came from (the `referrer` the invite page puts on the
 * Play Store address). */
class InstallReferrerReader(private val context: Context) {

    /** Success with the referrer text, or with null when there is none and never will be (a build
     * not installed from Play, or a Play version that can't say). Failure means Play couldn't be
     * reached right now, so the caller should ask again another time. */
    suspend fun read(): Result<String?> = suspendCancellableCoroutine { continuation ->
        val client = InstallReferrerClient.newBuilder(context).build()
        fun finish(result: Result<String?>) {
            runCatching { client.endConnection() }
            if (continuation.isActive) continuation.resume(result)
        }
        continuation.invokeOnCancellation { runCatching { client.endConnection() } }
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    when (responseCode) {
                        InstallReferrerClient.InstallReferrerResponse.OK ->
                            finish(runCatching { client.installReferrer.installReferrer }.map { it })
                        InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE ->
                            finish(Result.failure(IllegalStateException("Play is unavailable")))
                        // Not supported, or a mistake on our side: there will be no answer, ever.
                        else -> finish(Result.success(null))
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    finish(Result.failure(IllegalStateException("Play disconnected")))
                }
            })
        } catch (e: Exception) {
            finish(Result.failure(e))
        }
    }
}
