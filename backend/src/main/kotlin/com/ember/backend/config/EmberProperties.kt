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

@ConfigurationProperties(prefix = "ember.fcm")
data class FcmProperties(
    val credentialsPath: String,
    val enabled: Boolean,
)

@ConfigurationProperties(prefix = "ember.play-billing")
data class PlayBillingProperties(
    val packageName: String,
    val serviceAccountCredentialsPath: String,
    val enabled: Boolean,
)
