package com.ember.backend.service

import com.ember.backend.config.PlayBillingProperties
import com.ember.backend.exception.SubscriptionVerificationException
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.time.Instant

data class PlayPurchaseVerification(
    val isActive: Boolean,
    val expiresAt: Instant?,
)

@Service
class PlayBillingVerificationService(private val playBillingProperties: PlayBillingProperties) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val androidPublisher: AndroidPublisher? by lazy { initClient() }

    /** Inline JSON wins over the file path — see PlayBillingProperties for why both exist. */
    private fun credentialsStream(): InputStream? = when {
        playBillingProperties.serviceAccountCredentialsJson.isNotBlank() ->
            ByteArrayInputStream(playBillingProperties.serviceAccountCredentialsJson.toByteArray(Charsets.UTF_8))
        playBillingProperties.serviceAccountCredentialsPath.isNotBlank() ->
            FileInputStream(playBillingProperties.serviceAccountCredentialsPath)
        else -> null
    }

    private fun initClient(): AndroidPublisher? {
        if (!playBillingProperties.enabled) {
            logger.warn("Play Billing verification is disabled")
            return null
        }
        return try {
            val stream = credentialsStream()
            if (stream == null) {
                logger.error(
                    "Play Billing is enabled but no credentials are configured — set " +
                        "PLAY_SERVICE_ACCOUNT_CREDENTIALS_JSON. Every purchase verification will fail.",
                )
                return null
            }
            stream.use {
                val credentials = GoogleCredentials.fromStream(it)
                    .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))
                AndroidPublisher.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    HttpCredentialsAdapter(credentials),
                )
                    .setApplicationName("ember-backend")
                    .build()
            }
        } catch (ex: Exception) {
            logger.error("Failed to initialize Android Publisher client", ex)
            null
        }
    }

    fun verifySubscription(productId: String, purchaseToken: String): PlayPurchaseVerification {
        val client = androidPublisher
            ?: throw SubscriptionVerificationException("Play Billing verification is not configured on this server")

        val purchase = try {
            client.purchases().subscriptions()
                .get(playBillingProperties.packageName, productId, purchaseToken)
                .execute()
        } catch (ex: Exception) {
            logger.warn("Play Billing verification failed for product {}", productId, ex)
            throw SubscriptionVerificationException("Could not verify this purchase with Google Play")
        }

        val expiryMillis = purchase.expiryTimeMillis
        val expiresAt = expiryMillis?.let { Instant.ofEpochMilli(it) }
        val isActive = expiresAt?.isAfter(Instant.now()) == true

        return PlayPurchaseVerification(isActive = isActive, expiresAt = expiresAt)
    }
}
