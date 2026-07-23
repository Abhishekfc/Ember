package com.ember.app.widget

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ember.app.EmberApplication
import com.ember.app.data.UnauthorizedException
import java.util.concurrent.TimeUnit

private const val TAG = "WidgetUpdateWorker"

/** Was the *only* mechanism keeping the widget fresh, on a 30-minute floor — now that
 * EmberFirebaseMessagingService's NEW_PHOTO push updates the widget the instant a photo actually
 * arrives, this is a safety net for whatever that can't cover (the push never arriving, or
 * arriving while the device had no connectivity at all and nothing redelivers it), not the
 * primary path — hence the much longer interval below. Fetches the feed with the saved session
 * token and runs the same [WidgetPhotoSync] the app uses. Skips quietly when signed out, and
 * retries later on network failures.
 *
 * Goes through [EmberApplication]'s shared `networkModule`/`tokenStore` rather than constructing
 * its own — a previous version built a throwaway `NetworkModule(context)` per run, whose
 * `sessionExpired` flow nobody ever collected, so an expired token just silently 401'd here
 * forever (indefinitely, burning battery/data for a call that could never succeed) instead of
 * ever being cleared. */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as EmberApplication
        val tokenStore = app.networkModule.tokenStore
        if (tokenStore.currentToken() == null) return Result.success()

        // Goes through the shared PhotoRepository now (same one HomeViewModel uses), rather than
        // calling networkModule.api directly — this benefits from its short in-memory cache/
        // request-coalescing (see PhotoRepository) for free if a foreground fetch happens to be
        // running at the same moment. UnauthorizedException specifically distinguishes an
        // expired session from every other failure, exactly the distinction the previous
        // direct-response.code() check made — see PhotoRepository.getFeed and its own comment on
        // why that distinction matters here specifically.
        val items = app.photoRepository.getFeed().getOrElse { error ->
            if (error is UnauthorizedException) {
                Log.w(TAG, "Session expired, clearing token")
                tokenStore.clear()
                return Result.success()
            }
            Log.w(TAG, "Feed fetch failed, will retry", error)
            return Result.retry()
        }

        WidgetPhotoSync.sync(applicationContext, items)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "ember-widget-sync"

        /** Idempotent — calling this from every app launch and widget placement never duplicates
         * the job. UPDATE (not KEEP) specifically because the interval below just changed from a
         * 30-minute primary sync mechanism to a 6-hour safety net — KEEP would have silently left
         * anyone who already had this app installed stuck on the old 30-minute schedule forever,
         * since it explicitly leaves an already-scheduled job untouched. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
