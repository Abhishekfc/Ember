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
import com.ember.app.data.local.TokenStore
import com.ember.app.data.remote.NetworkModule
import java.util.concurrent.TimeUnit

private const val TAG = "WidgetUpdateWorker"

/** Periodic background refresh so the widget shows new photos without the app being opened:
 * fetches the feed with the saved session token and runs the same [WidgetPhotoSync] the app
 * uses. Skips quietly when signed out, and retries later on network failures. Android batches
 * periodic work, so "every 30 minutes" is a floor, not an exact cadence. */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (TokenStore(context).currentToken() == null) return Result.success()

        val response = runCatching { NetworkModule(context).api.getFeed() }.getOrElse {
            Log.w(TAG, "Feed fetch failed, will retry", it)
            return Result.retry()
        }
        // Auth or server errors aren't retryable from here — leave the widget as-is and let
        // the next scheduled run (or the app itself) catch things up.
        val items = response.body() ?: return Result.success()

        WidgetPhotoSync.sync(context, items)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "ember-widget-sync"

        /** Idempotent — KEEP means calling this from every app launch and widget placement
         * never resets the schedule, it just ensures one periodic job exists. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
