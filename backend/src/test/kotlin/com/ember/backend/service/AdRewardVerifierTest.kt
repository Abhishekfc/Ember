package com.ember.backend.service

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The callback address is public, so everything that makes a reward trustworthy is decided here:
 * a real Google signature, one of our own ad units, and a recent timestamp. Each test changes one
 * thing about an otherwise good callback.
 */
class AdRewardVerifierTest {

    private val fixture = AdCallbackFixture()
    private val verifier = fixture.verifier()

    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val userId = UUID.randomUUID()
    private val friendshipId = UUID.randomUUID()

    private fun goodQuery(at: Instant = now, transactionId: String = "tx-1") =
        fixture.query(userId, friendshipId, at.toEpochMilli(), transactionId = transactionId)

    @Test
    fun `a genuine callback is accepted and read correctly`() {
        val reward = verifier.verify(goodQuery(), now)

        assertEquals(VerifiedAdReward("tx-1", userId, friendshipId), reward)
    }

    @Test
    fun `changing anything after signing is refused`() {
        val tampered = goodQuery().replace(userId.toString(), UUID.randomUUID().toString())

        assertNull(verifier.verify(tampered, now))
    }

    @Test
    fun `a callback signed with someone else's key is refused`() {
        val query = fixture.query(userId, friendshipId, now.toEpochMilli(), signWith = AdCallbackFixture.newKeyPair().private)

        assertNull(verifier.verify(query, now))
    }

    @Test
    fun `a key id Google doesn't have is refused`() {
        val query = fixture.query(userId, friendshipId, now.toEpochMilli(), keyId = "1")

        assertNull(verifier.verify(query, now))
    }

    @Test
    fun `a callback with no signature is refused`() {
        val unsigned = goodQuery().substringBefore("&signature=")

        assertNull(verifier.verify(unsigned, now))
    }

    @Test
    fun `parameters added after the signature are refused`() {
        assertNull(verifier.verify(goodQuery() + "&user_id=${UUID.randomUUID()}", now))
    }

    @Test
    fun `a repeated parameter is refused even when the whole thing is signed`() {
        val query = fixture.query(userId, friendshipId, now.toEpochMilli(), extra = "&user_id=${UUID.randomUUID()}")

        assertNull(verifier.verify(query, now))
    }

    @Test
    fun `an ad unit that isn't ours is refused even with a genuine signature`() {
        // Anyone with an AdMob account can have Google sign callbacks for their own ads.
        val query = fixture.query(userId, friendshipId, now.toEpochMilli(), adUnit = "9999999999")

        assertNull(verifier.verify(query, now))
    }

    @Test
    fun `the full ad unit id in the settings works as well as the number`() {
        val verifier = fixture.verifier(rewardAdUnitIds = "ca-app-pub-1234567890123456/${fixture.adUnit}")

        assertEquals("tx-1", verifier.verify(goodQuery(), now)?.transactionId)
    }

    @Test
    fun `several ad units can be allowed`() {
        val verifier = fixture.verifier(rewardAdUnitIds = "111, ${fixture.adUnit} ,222")

        assertEquals("tx-1", verifier.verify(goodQuery(), now)?.transactionId)
    }

    @Test
    fun `with no ad units set nothing is accepted`() {
        assertNull(fixture.verifier(rewardAdUnitIds = "").verify(goodQuery(), now))
    }

    @Test
    fun `a callback older than a day is refused`() {
        assertNull(verifier.verify(goodQuery(at = now.minus(Duration.ofHours(25))), now))
    }

    @Test
    fun `a callback a few hours old is still accepted`() {
        // Google retries a failed callback for a while, so a late one is normal.
        assertEquals("tx-1", verifier.verify(goodQuery(at = now.minus(Duration.ofHours(3))), now)?.transactionId)
    }

    @Test
    fun `a callback dated in the future is refused`() {
        assertNull(verifier.verify(goodQuery(at = now.plus(Duration.ofMinutes(30))), now))
    }

    @Test
    fun `an id that isn't a uuid is refused`() {
        val query = fixture.query(userId, friendshipId, now.toEpochMilli()).let {
            val content = it.substringBefore("&signature=").replace(friendshipId.toString(), "not-a-uuid")
            "$content&signature=${fixture.sign(content)}&key_id=${fixture.keyId}"
        }

        assertNull(verifier.verify(query, now))
    }

    @Test
    fun `nothing or something huge is refused`() {
        assertNull(verifier.verify(null, now))
        assertNull(verifier.verify("", now))
        assertNull(verifier.verify("a".repeat(5000), now))
    }
}
