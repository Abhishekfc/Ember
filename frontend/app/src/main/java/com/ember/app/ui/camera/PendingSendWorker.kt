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
private const val KEY_SAVE = "save"
private const val KEY_PHOTO_ID = "photoId"
private const val KEY_ACTION = "action"
private const val ACTION_SAVE = "save"
private const val ACTION_ADD_RECIPIENTS = "addRecipients"

/** Bounds how many times a queued send retries before giving up — WorkManager's own
 * [WorkerParameters.runAttemptCount] already tracks this per work item, so this just caps it
 * rather than retrying a permanently-broken send (an expired session, a recipient removed since)
 * forever. This isn't a hardcoded delay — the actual wait between attempts is WorkManager's own
 * exponential backoff (see the enqueue functions below) — it only limits the total number of
 * tries. Shared by both worker classes in this file. */
private const val MAX_ATTEMPTS = 8

private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/** Uploads one already-captured photo in the background, independent of whether Camera (or the
 * app at all) is still open — see CameraViewModel.queueUpload, which hands off to this the moment
 * Send or the bookmark Save button is tapped instead of uploading inline and blocking the screen.
 *
 * [Constraints.setRequiredNetworkType] is what makes this genuinely offline-safe: WorkManager
 * itself won't even attempt to run this until the OS reports real connectivity, so there's no
 * hand-rolled "is it online" check or polling loop here — same idiom [WidgetUpdateWorker] already
 * uses for the widget's own background sync.
 *
 * The photo file must already live under [Context.getFilesDir], not the cache dir the live
 * capture flow otherwise uses — cacheDir can be cleared by the OS at any time, which would
 * silently lose a photo still waiting to send. See CameraViewModel.queueUpload for where that
 * move happens before this is ever enqueued.
 *
 * This is always the *first* upload for a given capture — see [AttachPhotoWorker] for what
 * happens when the other action (Save or Send, whichever wasn't tapped first) gets tapped
 * afterward for the very same capture. */
class PendingSendWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH)
        val recipientIds = inputData.getStringArray(KEY_RECIPIENT_IDS)?.toList()
        val save = inputData.getBoolean(KEY_SAVE, false)
        val file = filePath?.let { File(it) }
        if (file == null || recipientIds == null || !file.exists()) {
            Log.w(TAG, "Missing or already-gone queued photo, giving up on this send")
            return Result.failure()
        }

        val app = applicationContext as EmberApplication
        return app.photoRepository.uploadPhoto(file, recipientIds, save).fold(
            onSuccess = { response ->
                file.delete()
                // Lets a live HomeViewModel (if the app happens to be open) refresh Feed/Memories
                // immediately instead of only finding out on its own next unrelated trigger — see
                // EmberApplication.photoSendCompletedEvents' own doc comment. A no-op if nothing's
                // currently collecting it (app closed, or MainActivity not yet composed).
                app.notifyPhotoSendCompleted()
                // The output every chained AttachPhotoWorker actually depends on — see its own
                // doc comment for how WorkManager wires this in as that worker's own input.
                Result.success(workDataOf(KEY_PHOTO_ID to response.photoId))
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
        /** Enqueues the actual upload for a photo that's already been moved into durable storage
         * — called the instant the *first* of Save/Send is tapped for this capture, before any
         * network activity, so this returns immediately regardless of connectivity. [workName]
         * is a stable per-capture identity CameraViewModel decides once and passes to every
         * enqueue call for that capture — deliberately not [file]'s own name, which changes
         * (baking a caption in, Save's own copy) between when this primary upload is enqueued
         * and when a later attach call needs to target the exact same WorkManager unique-work
         * chain. [ExistingWorkPolicy.KEEP] means a stray duplicate call for a capture that's
         * already queued (there shouldn't be one — CameraViewModel's own hasQueuedUpload flag
         * guards this) is silently ignored rather than starting a second upload of the same
         * file. */
        fun enqueuePrimary(context: Context, workName: String, file: File, recipientIds: List<String>, save: Boolean) {
            val data = workDataOf(
                KEY_FILE_PATH to file.absolutePath,
                KEY_RECIPIENT_IDS to recipientIds.toTypedArray(),
                KEY_SAVE to save,
            )
            val request = OneTimeWorkRequestBuilder<PendingSendWorker>()
                .setInputData(data)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
        }

        /** Called when the bookmark Save button is tapped *after* Send already queued the primary
         * upload for this same capture — chains onto it (see [AttachPhotoWorker]) instead of
         * uploading the file a second time. [workName] must be the exact same value passed to
         * [enqueuePrimary] for this capture. */
        fun enqueueMarkSaved(context: Context, workName: String) {
            val data = workDataOf(KEY_ACTION to ACTION_SAVE)
            val request = OneTimeWorkRequestBuilder<AttachPhotoWorker>()
                .setInputData(data)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /** Called when Send is tapped *after* the bookmark Save button already queued the primary
         * upload for this same capture — chains onto it (see [AttachPhotoWorker]) instead of
         * uploading the file a second time. [workName] must be the exact same value passed to
         * [enqueuePrimary] for this capture. */
        fun enqueueAddRecipients(context: Context, workName: String, recipientIds: List<String>) {
            val data = workDataOf(KEY_ACTION to ACTION_ADD_RECIPIENTS, KEY_RECIPIENT_IDS to recipientIds.toTypedArray())
            val request = OneTimeWorkRequestBuilder<AttachPhotoWorker>()
                .setInputData(data)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

/** The "second action" for a capture that already got a [PendingSendWorker] upload queued for it
 * — either marking that same photo saved, or adding recipients to it, with no re-upload of the
 * file at all. Enqueued via [PendingSendWorker.enqueueMarkSaved]/[enqueueAddRecipients], both of
 * which use [ExistingWorkPolicy.APPEND_OR_REPLACE] on the *same unique work name* the primary
 * upload used — WorkManager's documented behavior for `APPEND`/`APPEND_OR_REPLACE` is exactly
 * "chain this after whatever's already enqueued under this name, and merge that work's own
 * output Data into this one's input Data." That's what actually delivers [KEY_PHOTO_ID] here:
 * it's never set explicitly at enqueue time, only ever arrives via that chained merge, which is
 * also why this never runs before the primary upload has genuinely succeeded — a failed primary
 * produces no output, and WorkManager fails every worker chained after a failed one automatically
 * without running them.
 *
 * If nothing was actually queued under that unique name (shouldn't happen given
 * CameraViewModel's own hasQueuedUpload guard, but not something this can trust blindly), this
 * runs standalone with no merged photoId and fails immediately rather than doing anything
 * unpredictable. */
class AttachPhotoWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val photoId = inputData.getString(KEY_PHOTO_ID)
        val action = inputData.getString(KEY_ACTION)
        if (photoId == null || action == null) {
            Log.w(TAG, "No photoId to attach to — the upload this depended on must have failed")
            return Result.failure()
        }

        val app = applicationContext as EmberApplication
        val result = when (action) {
            ACTION_SAVE -> app.photoRepository.markPhotoSaved(photoId)
            ACTION_ADD_RECIPIENTS -> {
                val recipientIds = inputData.getStringArray(KEY_RECIPIENT_IDS)?.toList().orEmpty()
                app.photoRepository.addPhotoRecipients(photoId, recipientIds)
            }
            else -> return Result.failure()
        }

        return result.fold(
            onSuccess = {
                // Same reasoning as PendingSendWorker's own call — a live HomeViewModel should
                // refresh Feed/Memories the moment this lands, regardless of which of the two
                // actions it was.
                app.notifyPhotoSendCompleted()
                Result.success()
            },
            onFailure = { error ->
                when {
                    error is UnauthorizedException -> {
                        Log.w(TAG, "Session expired, giving up on this attach")
                        app.networkModule.tokenStore.clear()
                        Result.failure()
                    }
                    runAttemptCount >= MAX_ATTEMPTS -> {
                        Log.w(TAG, "Giving up after $runAttemptCount attempts", error)
                        Result.failure()
                    }
                    else -> {
                        Log.w(TAG, "Attach failed, will retry", error)
                        Result.retry()
                    }
                }
            },
        )
    }
}
