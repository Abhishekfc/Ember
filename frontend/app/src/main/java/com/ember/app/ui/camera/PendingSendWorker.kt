package com.ember.app.ui.camera

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ember.app.EmberApplication
import com.ember.app.data.UnauthorizedException
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "PendingSendWorker"
private const val KEY_FILE_PATH = "filePath"
private const val KEY_RECIPIENT_IDS = "recipientIds"

/** Bounds how many times a queued send retries before giving up — WorkManager's own
 * [WorkerParameters.runAttemptCount] already tracks this per work item, so this just caps it
 * rather than retrying a permanently-broken send (an expired session, a recipient removed since)
 * forever. This isn't a hardcoded delay — the actual wait between attempts is WorkManager's own
 * exponential backoff (see [enqueue]) — it only limits the total number of tries. */
private const val MAX_ATTEMPTS = 8

/** Uploads one already-captured photo in the background, independent of whether Camera (or the
 * app at all) is still open — see CameraViewModel.sendCaptured, which hands off to this the
 * moment Send is tapped instead of uploading inline and blocking the screen.
 *
 * [Constraints.setRequiredNetworkType] is what makes this genuinely offline-safe: WorkManager
 * itself won't even attempt to run this until the OS reports real connectivity, so there's no
 * hand-rolled "is it online" check or polling loop here — same idiom [WidgetUpdateWorker] already
 * uses for the widget's own background sync.
 *
 * The photo file must already live under [Context.getFilesDir], not the cache dir the live
 * capture flow otherwise uses — cacheDir can be cleared by the OS at any time, which would
 * silently lose a photo still waiting to send. See CameraViewModel.sendCaptured for where that
 * move happens before this is ever enqueued. */
class PendingSendWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH)
        val recipientIds = inputData.getStringArray(KEY_RECIPIENT_IDS)?.toList()
        val file = filePath?.let { File(it) }
        if (file == null || recipientIds == null || !file.exists()) {
            Log.w(TAG, "Missing or already-gone queued photo, giving up on this send")
            return Result.failure()
        }

        val app = applicationContext as EmberApplication
        return app.photoRepository.uploadPhoto(file, recipientIds).fold(
            onSuccess = {
                file.delete()
                // Lets a live HomeViewModel (if the app happens to be open) refresh Feed/Memories
                // immediately instead of only finding out on its own next unrelated trigger — see
                // EmberApplication.photoSendCompletedEvents' own doc comment. A no-op if nothing's
                // currently collecting it (app closed, or MainActivity not yet composed).
                app.notifyPhotoSendCompleted()
                Result.success()
            },
            onFailure = { error ->
                when {
                    error is UnauthorizedException -> {
                        Log.w(TAG, "Session expired, giving up on this queued send")
                        app.networkModule.tokenStore.clear()
                        file.delete()
                        Result.failure()
                    }
                    runAttemptCount >= MAX_ATTEMPTS -> {
                        Log.w(TAG, "Giving up after $runAttemptCount attempts", error)
                        file.delete()
                        Result.failure()
                    }
                    else -> {
                        Log.w(TAG, "Queued send failed, will retry", error)
                        Result.retry()
                    }
                }
            },
        )
    }

    companion object {
        /** Shared by every queued send — lets the Camera screen observe how many are still in
         * flight (ENQUEUED or RUNNING) to drive its "Sending…" indicator directly from
         * WorkManager's own real state, rather than a second, separately-maintained count that
         * could drift from it. */
        const val TAG_PENDING_SEND = "pending_send"

        /** Enqueues the actual upload for a photo that's already been moved into durable storage
         * — called the instant Send is tapped, before any network activity, so this returns
         * immediately regardless of connectivity. [ExistingWorkPolicy.KEEP] with the file's own
         * name as the unique work name means this can never duplicate-enqueue the same photo. */
        fun enqueue(context: Context, file: File, recipientIds: List<String>) {
            val data = workDataOf(
                KEY_FILE_PATH to file.absolutePath,
                KEY_RECIPIENT_IDS to recipientIds.toTypedArray(),
            )
            val request = OneTimeWorkRequestBuilder<PendingSendWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .addTag(TAG_PENDING_SEND)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(file.name, ExistingWorkPolicy.KEEP, request)
        }
    }
}
