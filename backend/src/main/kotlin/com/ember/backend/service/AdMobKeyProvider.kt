package com.ember.backend.service

import com.ember.backend.config.AdProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/** Where [AdRewardVerifier] gets the public key that proves a callback really came from Google. */
interface AdMobKeyProvider {
    /** The key for [keyId], or null when Google has no such key (or it couldn't be fetched). */
    fun publicKey(keyId: String): PublicKey?
}

/** How long to wait before asking Google for its keys again after a miss. Without it, anyone
 * sending callbacks with made-up key ids would make this server call Google on every request. */
private val REFETCH_COOLDOWN: Duration = Duration.ofMinutes(5)

/** Google publishes the keys that sign reward callbacks at a public address and rotates them now
 * and then. They are fetched once and kept; an unknown key id is the only thing that triggers
 * another fetch, since a rotation shows up exactly as a callback signed with a new key. */
@Component
class GoogleAdMobKeyProvider(
    private val adProperties: AdProperties,
    private val objectMapper: ObjectMapper,
) : AdMobKeyProvider {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    private val keys = AtomicReference<Map<String, PublicKey>>(emptyMap())
    private var lastFetchAttempt: Instant = Instant.EPOCH

    override fun publicKey(keyId: String): PublicKey? {
        keys.get()[keyId]?.let { return it }
        refetchIfAllowed()
        return keys.get()[keyId]
    }

    @Synchronized
    private fun refetchIfAllowed() {
        val now = Instant.now()
        if (Duration.between(lastFetchAttempt, now) < REFETCH_COOLDOWN) return
        lastFetchAttempt = now
        runCatching { fetch() }
            .onSuccess { fetched -> if (fetched.isNotEmpty()) keys.set(fetched) }
            .onFailure { logger.warn("Could not fetch AdMob verifier keys: {}", it.message) }
    }

    private fun fetch(): Map<String, PublicKey> {
        val request = HttpRequest.newBuilder(URI.create(adProperties.verifierKeysUrl))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
        val keyFactory = KeyFactory.getInstance("EC")
        return objectMapper.readTree(response.body()).path("keys").associate { node ->
            val der = Base64.getDecoder().decode(node.path("base64").asText())
            node.path("keyId").asText() to keyFactory.generatePublic(X509EncodedKeySpec(der))
        }
    }
}
