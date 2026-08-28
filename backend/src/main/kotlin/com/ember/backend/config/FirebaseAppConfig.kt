package com.ember.backend.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream

/**
 * The one Firebase Admin SDK app instance this backend runs, shared by everything that talks to
 * Firebase: push notifications ([com.ember.backend.service.PushNotificationService]) and, now,
 * verifying the ID tokens issued by Firebase Authentication
 * ([com.ember.backend.security.FirebaseAuthenticationFilter]).
 *
 * Previously each service that needed Firebase built its own private [FirebaseApp], which is
 * fragile for a reason specific to the underlying SDK: [FirebaseApp.initializeApp] throws if
 * called more than once for the same app name, so two independent lazy initializers racing (or
 * simply both existing) had to each defensively check [FirebaseApp.getApps] first — logic that's
 * easy to get subtly wrong and only needs to exist once. Auth verification and push notifications
 * are also, in practice, always the same Firebase project's service account, so there was never a
 * reason for two separate credentials-loading code paths in the first place.
 *
 * Reuses [FcmProperties] for the credentials themselves — despite the name, "FCM" here just means
 * "the Firebase project," and [FcmProperties.enabled] is deliberately NOT consulted here: that
 * flag means "should this backend actually send push notifications," which has nothing to do with
 * whether Firebase Auth token verification — a core, always-needed capability now, not an
 * optional notification feature — should work. A backend with FCM disabled must still be able to
 * authenticate every request.
 */
@Configuration
class FirebaseAppConfig(private val fcmProperties: FcmProperties) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun firebaseApp(): FirebaseApp? {
        val stream = credentialsStream()
        if (stream == null) {
            logger.error(
                "No Firebase credentials configured — set FCM_CREDENTIALS_JSON (or " +
                    "FCM_CREDENTIALS_PATH). Push notifications and Firebase-authenticated " +
                    "requests will both fail.",
            )
            return null
        }
        return try {
            stream.use {
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(it))
                    .build()
                if (FirebaseApp.getApps().isEmpty()) FirebaseApp.initializeApp(options) else FirebaseApp.getInstance()
            }
        } catch (ex: Exception) {
            logger.error("Failed to initialize Firebase app", ex)
            null
        }
    }

    private fun credentialsStream(): InputStream? = when {
        fcmProperties.credentialsJson.isNotBlank() ->
            ByteArrayInputStream(fcmProperties.credentialsJson.toByteArray(Charsets.UTF_8))
        fcmProperties.credentialsPath.isNotBlank() -> FileInputStream(fcmProperties.credentialsPath)
        else -> null
    }
}
