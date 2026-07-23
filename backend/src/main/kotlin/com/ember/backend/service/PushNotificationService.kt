package com.ember.backend.service

import com.ember.backend.config.FcmProperties
import com.ember.backend.repository.DeviceTokenRepository
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.FileInputStream
import java.time.Instant
import java.util.UUID

@Service
class PushNotificationService(
    private val fcmProperties: FcmProperties,
    private val deviceTokenRepository: DeviceTokenRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val firebaseApp: FirebaseApp? by lazy { initFirebaseApp() }

    private fun initFirebaseApp(): FirebaseApp? {
        if (!fcmProperties.enabled || fcmProperties.credentialsPath.isBlank()) {
            logger.warn("FCM is disabled or ember.fcm.credentials-path is not set; push notifications are no-ops")
            return null
        }
        return try {
            FileInputStream(fcmProperties.credentialsPath).use { stream ->
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build()
                if (FirebaseApp.getApps().isEmpty()) FirebaseApp.initializeApp(options) else FirebaseApp.getInstance()
            }
        } catch (ex: Exception) {
            logger.error("Failed to initialize Firebase app; push notifications disabled", ex)
            null
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
            .putData("senderName", senderDisplayName)
            .putData("createdAt", createdAt.toString())
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .addAllTokens(tokens)
            .build()

        try {
            val response = FirebaseMessaging.getInstance(app).sendEachForMulticast(message)
            if (response.failureCount > 0) {
                logger.warn("FCM push: {} of {} messages failed", response.failureCount, tokens.size)
            }
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
            if (response.failureCount > 0) {
                logger.warn("FCM push: {} of {} messages failed", response.failureCount, tokens.size)
            }
        } catch (ex: Exception) {
            logger.error("Failed to send FCM push", ex)
        }
    }
}
