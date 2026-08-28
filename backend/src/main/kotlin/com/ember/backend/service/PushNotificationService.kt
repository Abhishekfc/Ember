package com.ember.backend.service

import com.ember.backend.repository.DeviceTokenRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class PushNotificationService(
    // Shared across every Firebase-touching service now — see FirebaseAppConfig's own doc
    // comment for why this used to be built privately here and no longer is. Null exactly when
    // Firebase credentials aren't configured, same meaning as before.
    private val firebaseApp: FirebaseApp?,
    private val deviceTokenRepository: DeviceTokenRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Records the outcome of a multicast send, deleting any token FCM reports as permanently dead.
     *
     * Without this, dead tokens accumulate forever and are re-sent to on every single push: a
     * device that uninstalled, or one whose token was issued by a Firebase project the backend no
     * longer authenticates against (exactly what happened moving from ember-app06 to emigo-85a07),
     * keeps its row indefinitely. That is wasted work per send, and it makes the failure count in
     * the logs permanently non-zero, which hides genuine delivery problems behind expected noise.
     *
     * Only [MessagingErrorCode.UNREGISTERED] and [MessagingErrorCode.INVALID_ARGUMENT] are treated
     * as fatal to the token. Everything else — quota, timeouts, FCM being briefly unavailable — is
     * transient and must *not* delete a perfectly good token just because one send failed.
     *
     * [tokens] must be in the same order they were added to the message: FCM returns responses
     * positionally, so index alignment is what maps a failure back to its token.
     */
    private fun recordSendResult(response: BatchResponse, tokens: List<String>, context: String) {
        if (response.failureCount == 0) return

        val dead = response.responses.withIndex().mapNotNull { (index, result) ->
            val code = result.exception?.messagingErrorCode
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                tokens.getOrNull(index)
            } else {
                null
            }
        }

        logger.warn(
            "FCM push ({}): {} of {} messages failed, {} token(s) permanently dead",
            context, response.failureCount, tokens.size, dead.size,
        )

        if (dead.isNotEmpty()) {
            // Best-effort: a cleanup failure must never propagate into the caller, which is always
            // a fire-and-forget path hanging off a real user action (a photo upload, a friend
            // request) that has already succeeded.
            runCatching { deviceTokenRepository.deleteByFcmTokenIn(dead) }
                .onFailure { logger.error("Failed to delete {} dead FCM token(s)", dead.size, it) }
        }
    }

    /** Fire-and-forget: push failures must never fail the photo upload that triggered them.
     *
     * Deliberately data-only — no `.setNotification(...)` — unlike the friend-request pushes
     * below. FCM's own behavior is the reason: a message carrying a `notification` payload is
     * handled directly by the OS whenever the app is backgrounded, and the app's own
     * `onMessageReceived` is never even invoked in that case — only a foregrounded app would see
     * it. Since the whole point of this specific push is to update the widget and sync state
     * *without* requiring the app to be open, it has to be data-only so the client's own handler
     * always runs. The client is responsible for showing its own "New photo from X" notification
     * (see EmberFirebaseMessagingService), which is why [senderDisplayName] is still included as
     * a data field. High priority so FCM/the device doesn't defer delivery. */
    fun notifyNewPhoto(
        photoId: UUID,
        photoUrl: String,
        senderId: UUID,
        senderDisplayName: String,
        createdAt: Instant,
        recipientUserIds: List<UUID>,
    ) {
        val app = firebaseApp ?: return
        val tokens = deviceTokenRepository.findAllByUserIdIn(recipientUserIds).map { it.fcmToken }
        if (tokens.isEmpty()) return

        val message = MulticastMessage.builder()
            .putData("type", "NEW_PHOTO")
            .putData("photoId", photoId.toString())
            .putData("photoUrl", photoUrl)
            .putData("senderId", senderId.toString())
            .putData("senderName", senderDisplayName)
            .putData("createdAt", createdAt.toString())
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .addAllTokens(tokens)
            .build()

        try {
            val response = FirebaseMessaging.getInstance(app).sendEachForMulticast(message)
            recordSendResult(response, tokens, "NEW_PHOTO")
        } catch (ex: Exception) {
            logger.error("Failed to send FCM push", ex)
        }
    }

    /** Data-only, same reasoning as [notifyNewPhoto]: the client has to build this notification
     * itself (with a "Restore streak" action button attached), and that only happens if
     * `onMessageReceived` actually runs, which FCM skips whenever the app is backgrounded and the
     * message carries a `notification` payload directly. [restoreDeadlineEpochSeconds] travels
     * with the payload so the client can set the notification's own auto-expiry to match the
     * server-side restore window exactly, rather than guessing a duration independently. */
    fun notifyStreakBroken(
        friendshipId: UUID,
        friendDisplayName: String,
        restoreDeadlineEpochSeconds: Long,
        recipientUserId: UUID,
    ) {
        val app = firebaseApp ?: return
        val tokens = deviceTokenRepository.findAllByUserIdIn(listOf(recipientUserId)).map { it.fcmToken }
        if (tokens.isEmpty()) return

        val message = MulticastMessage.builder()
            .putData("type", "STREAK_BROKEN")
            .putData("friendshipId", friendshipId.toString())
            .putData("friendName", friendDisplayName)
            .putData("restoreDeadlineEpochSeconds", restoreDeadlineEpochSeconds.toString())
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .addAllTokens(tokens)
            .build()

        try {
            val response = FirebaseMessaging.getInstance(app).sendEachForMulticast(message)
            recordSendResult(response, tokens, "STREAK_BROKEN")
        } catch (ex: Exception) {
            logger.error("Failed to send FCM push", ex)
        }
    }

    /** Ordinary visible notification — unlike [notifyNewPhoto], nothing here needs to run while
     * the app is closed (Friends/Activity already revalidate whenever they're next opened), so
     * there's no reason not to let the OS handle display the normal way. */
    fun notifyFriendRequestReceived(requesterDisplayName: String, recipientUserId: UUID) =
        sendSimpleNotification(
            title = "Friend request",
            body = "$requesterDisplayName wants to be friends",
            type = "FRIEND_REQUEST_RECEIVED",
            recipientUserId = recipientUserId,
        )

    fun notifyFriendRequestAccepted(accepterDisplayName: String, recipientUserId: UUID) =
        sendSimpleNotification(
            title = "Friend request accepted",
            body = "$accepterDisplayName accepted your friend request",
            type = "FRIEND_REQUEST_ACCEPTED",
            recipientUserId = recipientUserId,
        )

    // Reaction feature disabled — see PhotoReactionService's own comment.
    // fun notifyPhotoReaction(reactorDisplayName: String, emoji: String, recipientUserId: UUID) =
    //     sendSimpleNotification(
    //         title = "New reaction",
    //         body = "$reactorDisplayName reacted $emoji to your photo",
    //         type = "PHOTO_REACTION",
    //         recipientUserId = recipientUserId,
    //     )

    private fun sendSimpleNotification(title: String, body: String, type: String, recipientUserId: UUID) {
        val app = firebaseApp ?: return
        val tokens = deviceTokenRepository.findAllByUserIdIn(listOf(recipientUserId)).map { it.fcmToken }
        if (tokens.isEmpty()) return

        val message = MulticastMessage.builder()
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .putData("type", type)
            .addAllTokens(tokens)
            .build()

        try {
            val response = FirebaseMessaging.getInstance(app).sendEachForMulticast(message)
            recordSendResult(response, tokens, "FRIEND_EVENT")
        } catch (ex: Exception) {
            logger.error("Failed to send FCM push", ex)
        }
    }
}
