package com.ember.backend.service

import com.ember.backend.config.AdProperties
import com.ember.backend.exception.AdRewardRequiredException
import com.ember.backend.model.Friendship
import com.ember.backend.model.FriendshipStatus
import com.ember.backend.model.User
import com.ember.backend.repository.AdRewardRepository
import com.ember.backend.repository.FriendshipRepository
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One saved reward, kept in memory by [FakeRewards]. */
private class Row(
    val id: String,
    val userId: UUID,
    val friendshipId: UUID,
    val createdAt: Instant,
    var spentAt: Instant? = null,
)

/** The reward table, in memory, doing what the real queries do: adding a repeated id is a no-op,
 * and spending a reward works once. */
private class FakeRewards {
    val rows = mutableListOf<Row>()

    /** Runs just before a reward is spent, so a test can have a second restore get there first. */
    var beforeSpend: (String) -> Unit = {}

    val repository: AdRewardRepository = Proxy.newProxyInstance(
        AdRewardRepository::class.java.classLoader,
        arrayOf(AdRewardRepository::class.java),
    ) { _, method, args ->
        when (method.name) {
            "insertIfAbsent" -> {
                val id = args[0] as String
                if (rows.any { it.id == id }) 0 else {
                    rows += Row(id, args[1] as UUID, args[2] as UUID, args[3] as Instant)
                    1
                }
            }
            "findUnspentIds" -> {
                val since = args[2] as Instant
                val limit = (args[3] as org.springframework.data.domain.Pageable).pageSize
                rows.filter { it.userId == args[0] && it.friendshipId == args[1] && it.spentAt == null && it.createdAt >= since }
                    .sortedBy { it.createdAt }.take(limit).map { it.id }
            }
            "markSpent" -> {
                beforeSpend(args[0] as String)
                val row = rows.firstOrNull { it.id == args[0] && it.spentAt == null }
                if (row == null) 0 else {
                    row.spentAt = args[1] as Instant
                    1
                }
            }
            "deleteOlderThan" -> {
                val before = args[0] as Instant
                val old = rows.filter { it.createdAt < before }
                rows.removeAll(old.toSet())
                old.size
            }
            else -> throw UnsupportedOperationException(method.name)
        }
    } as AdRewardRepository
}

private fun friendships(friendship: Friendship?): FriendshipRepository = Proxy.newProxyInstance(
    FriendshipRepository::class.java.classLoader,
    arrayOf(FriendshipRepository::class.java),
) { _, method, _ ->
    when (method.name) {
        "findById" -> Optional.ofNullable(friendship)
        else -> throw UnsupportedOperationException(method.name)
    }
} as FriendshipRepository

class AdRewardServiceTest {

    private val fixture = AdCallbackFixture()
    private val now = Instant.parse("2026-10-08T12:00:00Z")

    private val me = User(email = "me@example.com", username = "me", displayName = "Me")
    private val friend = User(email = "friend@example.com", username = "friend", displayName = "Friend")
    private val friendship = Friendship(requester = me, addressee = friend, status = FriendshipStatus.ACCEPTED)

    private val rewards = FakeRewards()

    private fun service(friendship: Friendship? = this.friendship, adsRequired: Int = 3) = AdRewardService(
        rewards.repository,
        friendships(friendship),
        fixture.verifier(),
        AdProperties(restoreAdsRequired = adsRequired),
    )

    private fun callback(
        transactionId: String = "tx-1",
        userId: UUID = me.id,
        at: Instant = now,
    ) = fixture.query(userId, friendship.id, at.toEpochMilli(), transactionId = transactionId)

    /** Google confirming [count] watched ads. */
    private fun AdRewardService.watch(count: Int, at: Instant = now) {
        repeat(count) { assertEquals(RecordOutcome.RECORDED, record(callback("tx-${rewards.rows.size + 1}", at = at), at)) }
    }

    /** True when the restore may go ahead; false (with how far it got) when it was refused. */
    private fun AdRewardService.trySpend(userId: UUID = me.id, friendshipId: UUID = friendship.id, at: Instant = now): Boolean =
        try {
            spendRestoreRewards(userId, friendshipId, at)
            true
        } catch (_: AdRewardRequiredException) {
            false
        }

    @Test
    fun `three watched ads are recorded and pay for exactly one restore`() {
        val service = service()
        service.watch(3)

        assertTrue(service.trySpend())
        assertFalse(service.trySpend(), "three ads, one restore")
    }

    @Test
    fun `the same callback arriving twice records one reward`() {
        val service = service()

        assertEquals(RecordOutcome.RECORDED, service.record(callback(), now))
        assertEquals(RecordOutcome.DUPLICATE, service.record(callback(), now))
        service.watch(1)
        service.watch(1)

        assertEquals(3, rewards.rows.size, "the repeat added nothing")
        assertTrue(service.trySpend())
    }

    @Test
    fun `fewer ads than needed are refused and say how far along they are`() {
        val service = service()
        service.watch(2)

        val refusal = assertFailsWith<AdRewardRequiredException> { service.spendRestoreRewards(me.id, friendship.id, now) }

        assertEquals(3, refusal.adsRequired)
        assertEquals(2, refusal.adsWatched)
        assertTrue(rewards.rows.none { it.spentAt != null }, "nothing was used up")
    }

    @Test
    fun `nothing can be spent before Google has confirmed any ad`() {
        val refusal = assertFailsWith<AdRewardRequiredException> { service().spendRestoreRewards(me.id, friendship.id, now) }

        assertEquals(3, refusal.adsRequired)
        assertEquals(0, refusal.adsWatched)
    }

    @Test
    fun `six ads pay for two restores, and a seventh alone does not`() {
        val service = service()
        service.watch(6)

        assertTrue(service.trySpend())
        assertTrue(service.trySpend())
        service.watch(1)
        assertFalse(service.trySpend())
    }

    @Test
    fun `the number of ads a restore costs is a setting`() {
        val one = service(adsRequired = 1)
        one.watch(1)
        assertTrue(one.trySpend())

        rewards.rows.clear()
        val five = service(adsRequired = 5)
        five.watch(4)
        assertFalse(five.trySpend())
        five.watch(1)
        assertTrue(five.trySpend())
    }

    @Test
    fun `a nonsense setting still costs at least one ad`() {
        val service = service(adsRequired = 0)

        assertFalse(service.trySpend())
        service.watch(1)
        assertTrue(service.trySpend())
    }

    @Test
    fun `if another restore takes one of the ads first, this restore is refused`() {
        val service = service()
        service.watch(3)
        // Between looking and spending, a second restore takes the second ad.
        rewards.beforeSpend = { id -> if (id == "tx-2") rewards.rows.first { it.id == "tx-2" }.spentAt = now }

        val refusal = assertFailsWith<AdRewardRequiredException> { service.spendRestoreRewards(me.id, friendship.id, now) }

        assertEquals(3, refusal.adsRequired)
        assertEquals(2, refusal.adsWatched)
    }

    @Test
    fun `a forged callback records nothing`() {
        val forged = fixture.query(
            me.id, friendship.id, now.toEpochMilli(),
            signWith = AdCallbackFixture.newKeyPair().private,
        )

        assertEquals(RecordOutcome.INVALID, service().record(forged, now))
        assertTrue(rewards.rows.isEmpty())
    }

    @Test
    fun `a reward for a friendship the user isn't in records nothing`() {
        val stranger = UUID.randomUUID()

        assertEquals(RecordOutcome.IGNORED, service().record(callback(userId = stranger), now))
        assertTrue(rewards.rows.isEmpty())
    }

    @Test
    fun `a reward for a friendship that doesn't exist records nothing`() {
        assertEquals(RecordOutcome.IGNORED, service(friendship = null).record(callback(), now))
        assertTrue(rewards.rows.isEmpty())
    }

    @Test
    fun `a reward for a friend request that was never accepted records nothing`() {
        val pending = Friendship(id = friendship.id, requester = me, addressee = friend, status = FriendshipStatus.PENDING)

        assertEquals(RecordOutcome.IGNORED, service(pending).record(callback(), now))
        assertTrue(rewards.rows.isEmpty())
    }

    @Test
    fun `rewards can't be spent on a different friendship or by a different person`() {
        val service = service()
        service.watch(3)

        assertFalse(service.trySpend(friendshipId = UUID.randomUUID()))
        assertFalse(service.trySpend(userId = friend.id))
        assertTrue(service.trySpend())
    }

    @Test
    fun `rewards go stale after fifteen minutes`() {
        val service = service()
        service.watch(3)

        assertFalse(service.trySpend(at = now.plus(Duration.ofMinutes(16))))
        assertTrue(service.trySpend(at = now.plus(Duration.ofMinutes(14))))
    }

    @Test
    fun `ads watched a little apart still add up, as long as each is fresh`() {
        val service = service()
        service.watch(1, at = now)
        service.watch(1, at = now.plus(Duration.ofMinutes(8)))
        service.watch(1, at = now.plus(Duration.ofMinutes(16)))

        // The first is 16 minutes old by now, so only two count.
        assertFalse(service.trySpend(at = now.plus(Duration.ofMinutes(16))))
        service.watch(1, at = now.plus(Duration.ofMinutes(17)))
        assertTrue(service.trySpend(at = now.plus(Duration.ofMinutes(17))))
    }

    @Test
    fun `cleanup removes old records and keeps recent ones`() {
        val recent = Instant.now()
        rewards.rows += Row("old", me.id, friendship.id, recent.minus(Duration.ofDays(8)))
        rewards.rows += Row("new", me.id, friendship.id, recent.minus(Duration.ofDays(1)))

        service().deleteOldRecords()

        assertEquals(listOf("new"), rewards.rows.map { it.id })
    }
}
