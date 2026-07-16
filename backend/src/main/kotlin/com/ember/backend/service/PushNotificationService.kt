package com.ember.backend.service

import com.ember.backend.config.FcmProperties
import com.ember.backend.repository.DeviceTokenRepository
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.FileInputStream
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

    /** Fire-and-forget: push failures must never fail the photo upload that triggered them. */
    fun notifyNewPhoto(senderDisplayName: String, recipientUserIds: List<UUID>) {
        val app = firebaseApp ?: return
        val tokens = deviceTokenRepository.findAllByUserIdIn(recipientUserIds).map { it.fcmToken }
        if (tokens.isEmpty()) return

        val message = MulticastMessage.builder()
            .setNotification(
                Notification.builder()
                    .setTitle("New photo from $senderDisplayName")
                    .setBody("Tap to view")
                    .build()
            )
            .putData("type", "NEW_PHOTO")
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
