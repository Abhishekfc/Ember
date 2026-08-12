package com.ember.backend.security

import com.ember.backend.config.JwtProperties
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers token parsing, and specifically the `issuedAt` claim that early revocation depends on:
 * changing a password stamps a cutoff on the account and JwtAuthenticationFilter rejects any token
 * issued before it (see the V11 migration). That only works if a token's issue time survives a
 * round trip accurately enough to compare against.
 */
class JwtServiceTest {

    private val properties = JwtProperties(
        secret = "test-secret-value-that-is-at-least-32-bytes-long",
        accessTokenTtlMinutes = 10080,
        issuer = "ember-backend",
    )
    private val service = JwtService(properties)

    @Test
    fun `a token round-trips its user id`() {
        val userId = UUID.randomUUID()
        val parsed = service.parseToken(service.issueAccessToken(userId, "a@b.com"))
        assertEquals(userId, parsed?.userId)
    }

    @Test
    fun `a token carries an issue time at or after the moment it was created`() {
        // Truncated to seconds on the way out, since that is a JWT `iat` claim's precision — the
        // comparison in JwtAuthenticationFilter is written to tolerate exactly that.
        val before = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val parsed = service.parseToken(service.issueAccessToken(UUID.randomUUID(), "a@b.com"))
        assertNotNull(parsed)
        assertFalse(parsed.issuedAt.isBefore(before), "issuedAt ${parsed.issuedAt} predates $before")
    }

    @Test
    fun `a token issued now survives a cutoff stamped a moment earlier`() {
        // The exact sequence a password change performs: stamp the cutoff, then issue the
        // replacement. If truncation made these compare the wrong way, the device that just
        // changed its password would be signed out by its own action.
        val cutoff = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val parsed = service.parseToken(service.issueAccessToken(UUID.randomUUID(), "a@b.com"))
        assertNotNull(parsed)
        assertFalse(parsed.issuedAt.isBefore(cutoff), "the replacement token would be rejected by its own cutoff")
    }

    @Test
    fun `a token issued before a later cutoff is recognisably older`() {
        val parsed = service.parseToken(service.issueAccessToken(UUID.randomUUID(), "a@b.com"))
        assertNotNull(parsed)
        val laterCutoff = Instant.now().plusSeconds(5)
        assertTrue(parsed.issuedAt.isBefore(laterCutoff), "an older token was not seen as older than a later cutoff")
    }

    @Test
    fun `a token signed with a different secret is rejected`() {
        val other = JwtService(properties.copy(secret = "a-completely-different-secret-value-32-bytes"))
        val foreign = other.issueAccessToken(UUID.randomUUID(), "a@b.com")
        assertNull(service.parseToken(foreign))
    }

    @Test
    fun `garbage is rejected rather than throwing`() {
        listOf("", "not-a-token", "a.b.c", "Bearer x").forEach {
            assertNull(service.parseToken(it), "expected null for: $it")
        }
    }

    @Test
    fun `an expired token is rejected`() {
        val expired = JwtService(properties.copy(accessTokenTtlMinutes = -1))
        assertNull(service.parseToken(expired.issueAccessToken(UUID.randomUUID(), "a@b.com")))
    }
}
