package com.ember.backend.service

import com.ember.backend.config.AdProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.Signature
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** What a genuine callback says: this user watched an ad to restore this friendship's streak. */
data class VerifiedAdReward(val transactionId: String, val userId: UUID, val friendshipId: UUID)

private const val SIGNATURE_MARKER = "&signature="
private const val MAX_QUERY_LENGTH = 2048
private const val MAX_TRANSACTION_ID_LENGTH = 128

/** Google always ends the query with `signature=…&key_id=…`, in that order. */
private val SIGNATURE_TAIL = Regex("^([A-Za-z0-9_-]+)&key_id=(\\d+)$")

/** A callback older than this is refused even if its signature is good, so an old copy of a real
 * callback can't be replayed after [AdRewardService] has cleaned up the record of it. */
private val MAX_CALLBACK_AGE: Duration = Duration.ofHours(24)
private val MAX_CLOCK_SKEW: Duration = Duration.ofMinutes(5)

/**
 * Decides whether a request to the reward callback really is Google telling us an ad was watched.
 * Google signs the callback with a key it publishes (see [AdMobKeyProvider]); the signature covers
 * everything in the query except the signature and key id themselves, exactly as it was sent, which
 * is why this works on the raw query string and not on parsed parameters.
 *
 * A valid signature alone is not enough. Anyone with an AdMob account can make Google send signed
 * callbacks to any address, so the ad unit named in the callback must also be one of ours
 * ([AdProperties.rewardAdUnitIds]).
 */
@Component
class AdRewardVerifier(
    private val keyProvider: AdMobKeyProvider,
    adProperties: AdProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val allowedAdUnits: Set<String> = adProperties.rewardAdUnitIds
        .split(',')
        .map { adUnitNumber(it) }
        .filter { it.isNotEmpty() }
        .toSet()

    /** The reward the callback describes, or null if it can't be trusted for any reason. */
    fun verify(rawQuery: String?, now: Instant = Instant.now()): VerifiedAdReward? {
        if (rawQuery.isNullOrBlank() || rawQuery.length > MAX_QUERY_LENGTH) return reject("missing or oversized query")
        if (allowedAdUnits.isEmpty()) return reject("no reward ad units configured")

        val markerAt = rawQuery.lastIndexOf(SIGNATURE_MARKER)
        if (markerAt < 0) return reject("no signature")
        val tail = SIGNATURE_TAIL.matchEntire(rawQuery.substring(markerAt + SIGNATURE_MARKER.length))
            ?: return reject("malformed signature section")
        val (signatureText, keyId) = tail.destructured
        val signedContent = rawQuery.substring(0, markerAt)

        if (!isSignedByGoogle(signedContent, signatureText, keyId)) return reject("bad signature")

        val params = parseParams(signedContent) ?: return reject("malformed parameters")
        if (adUnitNumber(params["ad_unit"].orEmpty()) !in allowedAdUnits) return reject("ad unit is not ours")
        if (!isFresh(params["timestamp"], now)) return reject("stale or missing timestamp")

        val transactionId = params["transaction_id"]?.takeIf { it.isNotBlank() && it.length <= MAX_TRANSACTION_ID_LENGTH }
            ?: return reject("bad transaction id")
        val userId = params["user_id"]?.toUuidOrNull() ?: return reject("bad user id")
        val friendshipId = params["custom_data"]?.toUuidOrNull() ?: return reject("bad friendship id")
        return VerifiedAdReward(transactionId, userId, friendshipId)
    }

    private fun isSignedByGoogle(signedContent: String, signatureText: String, keyId: String): Boolean {
        val key = keyProvider.publicKey(keyId) ?: return false
        return runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(signedContent.toByteArray(StandardCharsets.UTF_8))
                verify(Base64.getUrlDecoder().decode(signatureText))
            }
        }.getOrDefault(false)
    }

    /** Null on a repeated parameter, so an extra copy of `user_id` slipped in can't pick which
     * value wins. */
    private fun parseParams(signedContent: String): Map<String, String>? = runCatching {
        val params = LinkedHashMap<String, String>()
        for (pair in signedContent.split('&')) {
            val name = URLDecoder.decode(pair.substringBefore('='), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(pair.substringAfter('=', ""), StandardCharsets.UTF_8)
            if (params.put(name, value) != null) return null
        }
        params
    }.getOrNull()

    private fun isFresh(timestampMillis: String?, now: Instant): Boolean {
        val sentAt = timestampMillis?.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: return false
        return sentAt.isAfter(now.minus(MAX_CALLBACK_AGE)) && sentAt.isBefore(now.plus(MAX_CLOCK_SKEW))
    }

    private fun reject(reason: String): VerifiedAdReward? {
        logger.warn("Ad reward callback refused: {}", reason)
        return null
    }
}

/** `ca-app-pub-123/456` and `456` both mean ad unit 456, which is what Google puts in a callback. */
internal fun adUnitNumber(adUnit: String): String = adUnit.trim().substringAfterLast('/')

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
