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
import java.io.FileInputStream
import java.time.Instant

data class PlayPurchaseVerification(
    val isActive: Boolean,
    val expiresAt: Instant?,
)

@Service
class PlayBillingVerificationService(private val playBillingProperties: PlayBillingProperties) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val androidPublisher: AndroidPublisher? by lazy { initClient() }

    private fun initClient(): AndroidPublisher? {
        if (!playBillingProperties.enabled || playBillingProperties.serviceAccountCredentialsPath.isBlank()) {
            logger.warn("Play Billing verification is disabled or credentials are not configured")
            return null
        }
        return try {
            FileInputStream(playBillingProperties.serviceAccountCredentialsPath).use { stream ->
                val credentials = GoogleCredentials.fromStream(stream)
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
