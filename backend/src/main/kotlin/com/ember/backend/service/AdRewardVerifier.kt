package com.ember.backend.service

import com.ember.backend.config.AdProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.PublicKey
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

    /** The reward the callback describes, or null if it isn't a trustworthy streak-restore reward
     * for any reason (see [inspect] for telling "not from Google" apart from "from Google, but not
     * a restore"). */
    fun verify(rawQuery: String?, now: Instant = Instant.now()): VerifiedAdReward? =
        (inspect(rawQuery, now) as? CallbackVerdict.Reward)?.reward

    /** Sorts a request to the callback into: a genuine restore reward, genuinely from Google but
     * about nothing we can use, or not to be trusted at all. The middle case is what AdMob's own
     * "Verify URL" test sends: correctly signed by Google, but for a made-up ad unit and, unless
     * typed in, with no user or friendship id. Google needs a 200 for it, and nothing is saved.
     * Only a request that isn't signed by Google (or is stale) is refused. A reward needs all of:
     * Google's signature, one of OUR ad units, a fresh timestamp, and a user and friendship. */
    fun inspect(rawQuery: String?, now: Instant = Instant.now()): CallbackVerdict {
        if (rawQuery.isNullOrBlank() || rawQuery.length > MAX_QUERY_LENGTH) return reject("missing or oversized query")
        if (allowedAdUnits.isEmpty()) return reject("no reward ad units configured")

        val markerAt = rawQuery.lastIndexOf(SIGNATURE_MARKER)
        if (markerAt < 0) return reject("no signature")
        val tail = SIGNATURE_TAIL.matchEntire(rawQuery.substring(markerAt + SIGNATURE_MARKER.length))
            ?: return reject("malformed signature section")
        val (signatureText, keyId) = tail.destructured
        val signedContent = rawQuery.substring(0, markerAt)

        val key = keyProvider.publicKey(keyId) ?: return reject("unknown key id $keyId")
        if (!isSignedBy(key, signedContent, signatureText)) return reject("bad signature")

        val params = parseParams(signedContent) ?: return reject("malformed parameters")
        if (!isFresh(params["timestamp"], now)) return reject("stale or missing timestamp")

        // Genuinely Google's word from here on. Anyone with an AdMob account can have Google sign a
        // callback for THEIR OWN ad unit and aim it at this address, naming anyone they like. Such a
        // callback must never become a reward, but it is not a forgery either, and AdMob's own
        // "Verify URL" test is exactly that (it uses a made-up ad unit, 1234567890): Google needs
        // a 200 for it. So a unit that isn't ours is answered "fine" and nothing is saved.
        if (adUnitNumber(params["ad_unit"].orEmpty()) !in allowedAdUnits) {
            logger.info("Ad reward callback is genuine but for an ad unit that isn't ours (a Verify test or a stranger's): nothing saved")
            return CallbackVerdict.GenuineButNotARestore
        }

        // One of our ad units. Whether it names a user and a friendship we can use is a separate
        // question, and "no" is not a reason to refuse.
        val transactionId = params["transaction_id"]?.takeIf { it.isNotBlank() && it.length <= MAX_TRANSACTION_ID_LENGTH }
        val userId = params["user_id"]?.toUuidOrNull()
        val friendshipId = params["custom_data"]?.toUuidOrNull()
        if (transactionId == null || userId == null || friendshipId == null) {
            logger.info("Ad reward callback is genuine but names no usable user or friendship (a Verify test): nothing saved")
            return CallbackVerdict.GenuineButNotARestore
        }
        return CallbackVerdict.Reward(VerifiedAdReward(transactionId, userId, friendshipId))
    }

    private fun isSignedBy(key: PublicKey, signedContent: String, signatureText: String): Boolean {
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

    private fun reject(reason: String): CallbackVerdict {
        logger.warn("Ad reward callback refused: {}", reason)
        return CallbackVerdict.NotTrusted
    }
}

/** What [AdRewardVerifier.inspect] decides about a request to the reward callback. */
sealed interface CallbackVerdict {
    /** Genuine, and it names the user and friendship a streak restore was earned for. */
    data class Reward(val reward: VerifiedAdReward) : CallbackVerdict

    /** Genuinely Google's word about one of our ad units, but with no usable user or friendship
     * (such as AdMob's own Verify test). Answered 200, saves nothing. */
    data object GenuineButNotARestore : CallbackVerdict

    /** Not from Google, not one of our ads, stale, or otherwise not to be trusted. Answered 403. */
    data object NotTrusted : CallbackVerdict
}

/** `ca-app-pub-123/456` and `456` both mean ad unit 456, which is what Google puts in a callback. */
internal fun adUnitNumber(adUnit: String): String = adUnit.trim().substringAfterLast('/')

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
