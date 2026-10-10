package com.emigo.app.invite

import com.emigo.app.data.remote.dto.FriendSearchResultDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeInviteStore(
    var checked: Boolean = false,
    var pending: String? = null,
) : InviteStore {
    override suspend fun hasCheckedReferrer() = checked
    override suspend fun markReferrerChecked() { checked = true }
    override suspend fun pendingInviter() = pending
    override suspend fun setPendingInviter(username: String?) { pending = username }
}

private fun person(
    username: String,
    displayName: String = "Ann",
    requested: Boolean = false,
    friendshipId: String? = null,
    isPendingFromThem: Boolean = false,
) = FriendSearchResultDto(
    userId = "u-$username",
    displayName = displayName,
    username = username,
    requested = requested,
    friendshipId = friendshipId,
    isPendingFromMe = requested,
    isPendingFromThem = isPendingFromThem,
)

/** A new install from "@ann's invite link": Play gives the app the link's referrer, which turns
 * into a one-tap offer to add ann. Every problem along the way means no offer, never an error. */
class InviteReferralTest {

    private class Harness(
        val store: FakeInviteStore = FakeInviteStore(),
        var referrer: Result<String?> = Result.success("invite=ann"),
        var searchResult: Result<List<FriendSearchResultDto>> = Result.success(listOf(person("ann"))),
        var sendResult: Result<Unit> = Result.success(Unit),
    ) {
        var referrerReads = 0
        var searches = mutableListOf<String>()
        var sentTo = mutableListOf<String>()

        val referral = InviteReferral(
            store = store,
            readInstallReferrer = { referrerReads++; referrer },
            search = { query -> searches += query; searchResult },
            sendRequest = { userId -> sentTo += userId; sendResult },
        )
    }

    @Test
    fun anInstallFromAnInviteLinkOffersToAddTheInviter() = runBlocking {
        val h = Harness()

        val inviter = h.referral.inviterToOffer()

        assertEquals(Inviter(userId = "u-ann", username = "ann", displayName = "Ann"), inviter)
        assertEquals(listOf("ann"), h.searches)
    }

    @Test
    fun acceptingSendsTheRequestAndForgetsTheInvite() = runBlocking {
        val h = Harness()
        val inviter = h.referral.inviterToOffer()!!

        val result = h.referral.accept(inviter)

        assertTrue(result.isSuccess)
        assertEquals(listOf("u-ann"), h.sentTo)
        assertNull(h.store.pending)
    }

    @Test
    fun aFailedRequestKeepsTheInviteSoItCanBeTriedAgain() = runBlocking {
        val h = Harness(sendResult = Result.failure(Exception("offline")))
        val inviter = h.referral.inviterToOffer()!!

        val result = h.referral.accept(inviter)

        assertTrue(result.isFailure)
        assertEquals("ann", h.store.pending)
    }

    @Test
    fun notNowForgetsTheInvite() = runBlocking {
        val h = Harness()
        h.referral.inviterToOffer()

        h.referral.dismiss()

        assertNull(h.store.pending)
        assertNull(h.referral.inviterToOffer())
    }

    @Test
    fun theReferrerIsReadOnceAndNeverAgain() = runBlocking {
        val h = Harness()

        h.referral.inviterToOffer()
        h.referral.inviterToOffer()
        h.referral.inviterToOffer()

        assertEquals(1, h.referrerReads)
        assertTrue(h.store.checked)
    }

    @Test
    fun anInstallWithNoInviteOffersNothing() = runBlocking {
        val h = Harness(referrer = Result.success(null))

        assertNull(h.referral.inviterToOffer())
        assertTrue("answered, so not asked again", h.store.checked)
        assertTrue("no lookup without a name", h.searches.isEmpty())
    }

    @Test
    fun aReferrerWithoutAnInviteOffersNothing() = runBlocking {
        val h = Harness(referrer = Result.success("utm_source=google-play&utm_medium=organic"))

        assertNull(h.referral.inviterToOffer())
        assertTrue(h.searches.isEmpty())
    }

    @Test
    fun aDamagedReferrerOffersNothing() = runBlocking {
        val h = Harness(referrer = Result.success("invite=../../x"))

        assertNull(h.referral.inviterToOffer())
        assertTrue(h.searches.isEmpty())
    }

    @Test
    fun whenPlayCannotAnswerRightNowItIsAskedAgainNextTime() = runBlocking {
        val h = Harness(referrer = Result.failure(Exception("Play is unavailable")))

        assertNull(h.referral.inviterToOffer())
        assertFalse("not marked as checked", h.store.checked)

        h.referrer = Result.success("invite=ann")
        assertEquals("ann", h.referral.inviterToOffer()?.username)
    }

    @Test
    fun aLookupThatFailedOnTheNetworkKeepsTheInviteForNextTime() = runBlocking {
        val h = Harness(searchResult = Result.failure(Exception("offline")))

        assertNull(h.referral.inviterToOffer())
        assertEquals("ann", h.store.pending)

        h.searchResult = Result.success(listOf(person("ann")))
        assertEquals("ann", h.referral.inviterToOffer()?.username)
        assertEquals("only one read of the referrer", 1, h.referrerReads)
    }

    @Test
    fun anInviterWhoIsAlreadyAFriendIsNotOffered() = runBlocking {
        val h = Harness(searchResult = Result.success(listOf(person("ann", friendshipId = "f1"))))

        assertNull(h.referral.inviterToOffer())
        assertNull("forgotten", h.store.pending)
    }

    @Test
    fun anInviterWhoAlreadyHasARequestFromMeIsNotOffered() = runBlocking {
        val h = Harness(searchResult = Result.success(listOf(person("ann", requested = true, friendshipId = "f1"))))

        assertNull(h.referral.inviterToOffer())
        assertNull(h.store.pending)
    }

    @Test
    fun anInviterWhoAlreadyRequestedMeIsNotOffered() = runBlocking {
        val h = Harness(searchResult = Result.success(listOf(person("ann", friendshipId = "f1", isPendingFromThem = true))))

        assertNull(h.referral.inviterToOffer())
        assertNull(h.store.pending)
    }

    @Test
    fun anInviterWhoIsNotFoundIsForgotten() = runBlocking {
        val h = Harness(searchResult = Result.success(emptyList()))

        assertNull(h.referral.inviterToOffer())
        assertNull(h.store.pending)
    }

    @Test
    fun onlyTheExactUsernameCountsNotALookalike() = runBlocking {
        val h = Harness(searchResult = Result.success(listOf(person("anna"), person("ann2"))))

        assertNull("anna and ann2 are not ann", h.referral.inviterToOffer())
    }

    @Test
    fun theUsernameMatchIgnoresCapitals() = runBlocking {
        val h = Harness(
            referrer = Result.success("invite=ANN"),
            searchResult = Result.success(listOf(person("ann", displayName = "Ann Lee"))),
        )

        assertEquals("Ann Lee", h.referral.inviterToOffer()?.displayName)
    }

    @Test
    fun theRightPersonIsPickedFromSeveralResults() = runBlocking {
        val h = Harness(searchResult = Result.success(listOf(person("anna"), person("ann", displayName = "The Real Ann"), person("ann2"))))

        assertEquals("The Real Ann", h.referral.inviterToOffer()?.displayName)
    }

    @Test
    fun anInviteLeftFromAnEarlierLaunchIsStillOffered() = runBlocking {
        // The referrer was read last time, and the lookup then failed offline.
        val h = Harness(store = FakeInviteStore(checked = true, pending = "ann"))

        assertEquals("ann", h.referral.inviterToOffer()?.username)
        assertEquals("not read again", 0, h.referrerReads)
    }
}
