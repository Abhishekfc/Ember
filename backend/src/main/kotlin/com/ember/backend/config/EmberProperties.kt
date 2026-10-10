package com.ember.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

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

/** [rewardAdUnitIds] is a comma-separated list of the AdMob ad units whose watched ads count as a
 * reward here (the numbers after the slash in `ca-app-pub-…/1234567890` work, and so do the full
 * ids). It is the check that stops anyone else's AdMob app from pointing Google's signed callbacks
 * at this server, so blank means every callback is refused, never that every callback is allowed. */
@ConfigurationProperties(prefix = "ember.ads")
data class AdProperties(
    val rewardAdUnitIds: String = "",
    val verifierKeysUrl: String = "https://www.gstatic.com/admob/reward/verifier-keys.json",
    /** How many watched ads one streak restore costs. The app learns this number from the server,
     * so changing it here needs no app update. */
    val restoreAdsRequired: Int = 3,
)

/** [alertEmail] blank means moderation alerts are simply off — [ReportService] checks this
 * itself before ever touching [com.ember.backend.service.EmailService], so an unconfigured
 * sender (the common state before this is set up) never has to fail loudly; reports keep saving
 * to the DB either way, this is purely an added notification. */
@ConfigurationProperties(prefix = "ember.moderation")
data class ModerationProperties(
    val alertEmail: String = "",
)

/** [apiKey] blank means [com.ember.backend.service.EmailService] itself is a no-op — same
 * "unconfigured means silently off" shape as [ModerationProperties.alertEmail]. [fromEmail]
 * defaults to Resend's own shared sandbox sender, which works with no domain setup on Resend's
 * side at all; switch it to a verified `@emigo.live` address once that's set up there for a
 * properly branded sender instead. */
@ConfigurationProperties(prefix = "ember.resend")
data class ResendProperties(
    val apiKey: String = "",
    val fromEmail: String = "Emigo <onboarding@resend.dev>",
    val apiUrl: String = "https://api.resend.com/emails",
)
