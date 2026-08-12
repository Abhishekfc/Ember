package com.ember.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ember.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenTtlMinutes: Long,
    val issuer: String,
)

@ConfigurationProperties(prefix = "ember.storage.r2")
data class R2Properties(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val publicBaseUrl: String,
)

/**
 * [credentialsJson] is the same service-account JSON [credentialsPath] points at, supplied inline
 * instead of as a file, and takes precedence when both are set.
 *
 * It exists because not every host can give the app a file. Railway has no secret-file feature at
 * all — only environment variables — so a path-only configuration silently produced no credentials
 * there, and FCM disabled itself with nothing but a warning in the log: the deploy looks healthy
 * and no notification is ever delivered. Locally a file is still the nicer option, so both work.
 */
@ConfigurationProperties(prefix = "ember.fcm")
data class FcmProperties(
    val credentialsPath: String,
    val credentialsJson: String = "",
    val enabled: Boolean,
)

/** [serviceAccountCredentialsJson] is the inline counterpart to [serviceAccountCredentialsPath],
 * for the same reason [FcmProperties.credentialsJson] exists — and it matters more here: without
 * working credentials, Gold purchase verification doesn't degrade quietly, it rejects every real
 * purchase a paying customer makes. */
@ConfigurationProperties(prefix = "ember.play-billing")
data class PlayBillingProperties(
    val packageName: String,
    val serviceAccountCredentialsPath: String,
    val serviceAccountCredentialsJson: String = "",
    val enabled: Boolean,
)
