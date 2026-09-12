package com.ember.backend.service

import com.ember.backend.config.PlayBillingProperties
import com.ember.backend.exception.SubscriptionVerificationException
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest
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
    /** True once Google has recorded that we've delivered this purchase. An unacknowledged
     * purchase is auto-refunded by Play after 3 days, so [SubscriptionService.verify] acknowledges
     * it right after granting the entitlement. */
    val isAcknowledged: Boolean,
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
        // paymentState: 0 = payment pending, 1 = received, 2 = free trial, 3 = pending deferred
        // upgrade/downgrade. It's absent entirely once the subscription reaches a terminal
        // (expired / fully cancelled) state, where the expiry check below is what actually governs.
        // A pending payment (some carrier billing, cash, etc.) must NOT count as active yet.
        val paymentPending = purchase.paymentState == 0
        val isActive = expiresAt?.isAfter(Instant.now()) == true && !paymentPending

        return PlayPurchaseVerification(
            isActive = isActive,
            expiresAt = expiresAt,
            isAcknowledged = purchase.acknowledgementState == 1,
        )
    }

    /** Best-effort — a purchase that's already acknowledged (a retry, or the client got there
     * first) throws here too, which is harmless: the entitlement is granted either way, and this
     * only exists to stop Play's 3-day auto-refund of an unacknowledged purchase. */
    fun acknowledge(productId: String, purchaseToken: String) {
        val client = androidPublisher ?: return
        try {
            client.purchases().subscriptions()
                .acknowledge(
                    playBillingProperties.packageName,
                    productId,
                    purchaseToken,
                    SubscriptionPurchasesAcknowledgeRequest(),
                )
                .execute()
        } catch (ex: Exception) {
            logger.warn("Could not acknowledge purchase for product {}", productId, ex)
        }
    }
}
