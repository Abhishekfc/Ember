package com.emigo.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.emigo.app.EXTRA_NOTIFICATION_ACTION
import com.emigo.app.EXTRA_STREAK_FRIENDSHIP_ID
import com.emigo.app.EmberApplication
import com.emigo.app.MainActivity
import com.emigo.app.NEW_PHOTO_NOTIFICATION_CHANNEL_ID
import com.emigo.app.NOTIFICATION_ACTION_RESTORE_STREAK
import com.emigo.app.R
import com.emigo.app.STREAK_NOTIFICATION_CHANNEL_ID
import com.emigo.app.widget.WidgetPhotoSync
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val NEW_PHOTO_NOTIFICATION_ID = 1001

/** Handles FCM messages even when the app process isn't otherwise running — see
 * PushNotificationService.notifyNewPhoto on the backend for why NEW_PHOTO specifically has to be
 * data-only to guarantee this actually gets invoked while the app is backgrounded (a message
 * carrying its own `notification` payload is instead handled by the OS directly in that case,
 * and this method is never called at all). Friend-request pushes deliberately stay
 * notification-style and never reach here while backgrounded — nothing about them needs to run
 * without the app actually being open; Friends/Activity already revalidate on their own next
 * open regardless. STREAK_BROKEN is data-only for the same reason NEW_PHOTO is: it needs a real
 * action button attached, which only this handler (not the OS's own auto-display path) can build. */
class EmberFirebaseMessagingService : FirebaseMessagingService() {

    // This service can be created and torn down independent of any Activity/ViewModel lifecycle
    // (including while the app process would otherwise be dead), so it needs its own scope
    // rather than borrowing one from something that might not exist right now.
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = application as EmberApplication
        scope.launch {
            // If nobody's signed in yet, there's nothing to register against — MainActivity
            // fetches and registers the current token itself the moment a session becomes
            // authenticated (see its own LaunchedEffect(authenticated)), covering that case.
            if (app.networkModule.tokenStore.currentToken() != null) {
                app.authRepository.registerDeviceToken(token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        when (message.data["type"]) {
            "NEW_PHOTO" -> handleNewPhoto(message)
            "STREAK_BROKEN" -> handleStreakBroken(message)
        }
    }

    private fun handleNewPhoto(message: RemoteMessage) {
        val photoId = message.data["photoId"] ?: return
        val photoUrl = message.data["photoUrl"] ?: return
        val senderId = message.data["senderId"] ?: return
        val senderName = message.data["senderName"] ?: return
        val createdAt = message.data["createdAt"] ?: return

        val app = application as EmberApplication
        scope.launch {
            // Updates the widget directly from the payload — no feed refetch needed for that —
            // and separately pings any *live* HomeViewModel to do its own real refetch, which
            // only ever updates syncedFeedItems (see HomeViewModel), never the visible browsing
            // session, so this can't interrupt someone mid-swipe through Home. senderId is what
            // lets the widget honor a Gold subscriber's featured-friend choice on this path too —
            // see WidgetPhotoSync.syncFromPush's own doc comment.
            WidgetPhotoSync.syncFromPush(app, photoId = photoId, photoUrl = photoUrl, senderId = senderId, senderName = senderName, createdAtIso = createdAt)
            app.notifyNewPhotoPush()
        }
        showNewPhotoNotification(senderName)
    }

    private fun handleStreakBroken(message: RemoteMessage) {
        val friendshipId = message.data["friendshipId"] ?: return
        val friendName = message.data["friendName"] ?: return
        val restoreDeadlineEpochSeconds = message.data["restoreDeadlineEpochSeconds"]?.toLongOrNull() ?: return
        showStreakBrokenNotification(friendshipId, friendName, restoreDeadlineEpochSeconds)
    }

    /** Stands in for the visible notification FCM used to show automatically before this push
     * became data-only (see PushNotificationService.notifyNewPhoto for why it had to change) —
     * without this, switching to data-only would have silently dropped a notification users
     * already see today. */
    private fun showNewPhotoNotification(senderName: String) {
        if (!hasNotificationPermission()) return

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NEW_PHOTO_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New photo from $senderName")
            .setContentText("Tap to view")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()
        NotificationManagerCompat.from(this).notify(NEW_PHOTO_NOTIFICATION_ID, notification)
    }

    /** The one notification in the app with a real action button — see MainActivity's own
     * handling of [EXTRA_NOTIFICATION_ACTION] for what tapping it actually does (restore the
     * streak if Gold, redirect to the Gold screen otherwise; that branch lives there, not here,
     * since it needs a live ViewModel/subscription check this background service has no access
     * to). [restoreDeadlineEpochSeconds] drives [NotificationCompat.Builder.setTimeoutAfter] so
     * the notification itself disappears from the tray once the restore window server-side has
     * actually closed, rather than sitting there indefinitely offering an action that would just
     * fail if tapped. */
    private fun showStreakBrokenNotification(friendshipId: String, friendName: String, restoreDeadlineEpochSeconds: Long) {
        if (!hasNotificationPermission()) return

        // Distinct request codes for the two PendingIntents targeting the same Activity class —
        // without this, Android can treat them as the same PendingIntent and silently keep
        // whichever extras were set first, which would make the action button's own
        // friendshipId/action extras never actually arrive.
        val contentIntent = PendingIntent.getActivity(
            this,
            friendshipId.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val restoreIntent = PendingIntent.getActivity(
            this,
            friendshipId.hashCode() xor NOTIFICATION_ACTION_RESTORE_STREAK.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NOTIFICATION_ACTION, NOTIFICATION_ACTION_RESTORE_STREAK)
                putExtra(EXTRA_STREAK_FRIENDSHIP_ID, friendshipId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val timeoutMillis = (restoreDeadlineEpochSeconds * 1000) - System.currentTimeMillis()

        val notification = NotificationCompat.Builder(this, STREAK_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Your streak with $friendName broke")
            .setContentText("Restore it before it's gone for good")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Restore streak", restoreIntent)
            .apply { if (timeoutMillis > 0) setTimeoutAfter(timeoutMillis) }
            .build()
        NotificationManagerCompat.from(this).notify(friendshipId.hashCode(), notification)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
