package com.emigo.app.ads

import android.app.Activity
import com.emigo.app.data.AdRewardRequiredException
import com.emigo.app.data.adRewardRequired
import com.emigo.app.data.remote.dto.ErrorResponse
import com.emigo.app.data.remote.dto.FriendSummaryDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun friend(streak: Int) = FriendSummaryDto(
    friendshipId = "f1",
    friendId = "u2",
    displayName = "Ann",
    username = "ann",
    profilePhotoUrl = null,
    pinnedByMe = false,
    pinnedByThem = false,
    lastActivityAt = null,
    lastActivityBySelf = null,
    streak = streak,
)

private class FakeLoadedAd(private val result: ShowResult, private val onShown: () -> Unit = {}) : LoadedRewardedAd {
    var userId: String? = "not shown"
    var customData: String? = "not shown"

    override suspend fun show(activity: Activity, userId: String?, customData: String?): ShowResult {
        this.userId = userId
        this.customData = customData
        onShown()
        return result
    }
}

/** Hands out the ads one by one, the way the real SDK loads a fresh ad each time; once they run
 * out, no ad can be had. */
private class FakeAds(private val queue: List<LoadedRewardedAd?>) : RewardedAds {
    constructor(ad: LoadedRewardedAd?) : this(listOf(ad))

    var loadCount = 0

    override suspend fun load(activity: Activity, adUnitId: String): LoadedRewardedAd? =
        queue.getOrElse(loadCount++) { queue.lastOrNull() }
}

/** The server restores a streak only once Google has confirmed every watched ad, so each test plays
 * the server's side by deciding what each restore request answers. */
class RestoreStreakWithAdTest {

    private val activity = object : Activity() {}

    /** Answers the next call from [answers], repeating the last one when they run out. */
    private class ServerScript(private val answers: List<Result<FriendSummaryDto>>) {
        var calls = 0
        suspend fun restore(friendshipId: String): Result<FriendSummaryDto> = answers[minOf(calls++, answers.lastIndex)]
    }

    /** The server's "not enough ads yet", saying how many it takes and how many it has. */
    private fun needs(required: Int = 3, watched: Int = 0) =
        Result.failure<FriendSummaryDto>(AdRewardRequiredException(required, watched))

    private val needsAd = needs(required = 1)

    private fun flow(ads: RewardedAds, server: ServerScript, maxAttempts: Int = 5) = RestoreStreakWithAd(
        ads = ads,
        adUnitId = "unit",
        myUserId = { Result.success("me") },
        restore = server::restore,
        retryDelayMillis = 0,
        maxAttempts = maxAttempts,
    )

    private fun earned() = FakeLoadedAd(ShowResult.EARNED)

    @Test
    fun watchingAnAdRestoresTheStreakOnceTheServerAgrees() = runBlocking {
        // One ad takes it: not yet after the ad ends, then Google's word arrives.
        val server = ServerScript(listOf(needsAd, needsAd, needsAd, Result.success(friend(streak = 7))))

        val result = flow(FakeAds(earned()), server).run(activity, "f1")

        assertEquals(AdRestoreResult.Restored(friend(7)), result)
    }

    @Test
    fun threeAdsAreShownOneAfterAnotherWhenTheServerAsksForThree() = runBlocking {
        val ads = FakeAds(listOf(earned(), earned(), earned()))
        val server = ServerScript(listOf(needs(3, 0), needs(3, 2), Result.success(friend(streak = 9))))

        val result = flow(ads, server).run(activity, "f1")

        assertEquals(AdRestoreResult.Restored(friend(9)), result)
        assertEquals(3, ads.loadCount)
    }

    @Test
    fun theSheetIsToldWhichAdIsComingUp() = runBlocking {
        val seen = mutableListOf<AdProgress>()
        val server = ServerScript(listOf(needs(3, 0), Result.success(friend(1))))

        flow(FakeAds(listOf(earned(), earned(), earned())), server).run(activity, "f1", onProgress = { seen += it })

        assertEquals(listOf(AdProgress(1, 3), AdProgress(2, 3), AdProgress(3, 3)), seen)
    }

    @Test
    fun adsAlreadyOnRecordAreNotAskedForAgain() = runBlocking {
        // Two of three were watched earlier (say the viewer stopped halfway): only one more is shown.
        val ads = FakeAds(listOf(earned(), earned(), earned()))
        val seen = mutableListOf<AdProgress>()
        val server = ServerScript(listOf(needs(3, 2), Result.success(friend(4))))

        val result = flow(ads, server).run(activity, "f1", onProgress = { seen += it })

        assertEquals(AdRestoreResult.Restored(friend(4)), result)
        assertEquals(1, ads.loadCount)
        assertEquals(listOf(AdProgress(3, 3)), seen)
    }

    @Test
    fun theNumberOfAdsFollowsTheServer() = runBlocking {
        // The server can change the number (say to 5) without an app update.
        val ads = FakeAds(List(5) { earned() })
        val server = ServerScript(listOf(needs(5, 0), Result.success(friend(2))))

        flow(ads, server).run(activity, "f1")

        assertEquals(5, ads.loadCount)
    }

    @Test
    fun everyAdCarriesTheUserAndTheFriendshipForTheServerToMatch() = runBlocking {
        val first = earned()
        val second = earned()

        flow(FakeAds(listOf(first, second)), ServerScript(listOf(needs(2, 0), Result.success(friend(1))))).run(activity, "f1")

        for (ad in listOf(first, second)) {
            assertEquals("me", ad.userId)
            assertEquals("f1", ad.customData)
        }
    }

    @Test
    fun noAdIsShownWhenTheServerAlreadyHasEnoughOnRecord() = runBlocking {
        // Confirmations that arrived late last time, or Gold.
        val ads = FakeAds(earned())

        val result = flow(ads, ServerScript(listOf(Result.success(friend(3))))).run(activity, "f1")

        assertEquals(AdRestoreResult.Restored(friend(3)), result)
        assertEquals(0, ads.loadCount)
    }

    @Test
    fun noAdIsShownWhenTheRestoreCanNeverHappen() = runBlocking {
        // The restore window has closed: watching ads would be wasted.
        val ads = FakeAds(earned())
        val gone = Result.failure<FriendSummaryDto>(Exception("This streak can't be restored anymore"))

        val result = flow(ads, ServerScript(listOf(gone))).run(activity, "f1")

        assertEquals(AdRestoreResult.Failed("This streak can't be restored anymore"), result)
        assertEquals(0, ads.loadCount)
    }

    @Test
    fun closingTheAdEarlyRestoresNothing() = runBlocking {
        val server = ServerScript(listOf(needsAd))

        val result = flow(FakeAds(FakeLoadedAd(ShowResult.CLOSED_EARLY)), server).run(activity, "f1")

        assertEquals(AdRestoreResult.AdClosedEarly, result)
        assertEquals("only the first check, no waiting for a reward that was never earned", 1, server.calls)
    }

    @Test
    fun closingTheThirdAdEarlyStopsThereAndShowsNoMore() = runBlocking {
        val ads = FakeAds(listOf(earned(), earned(), FakeLoadedAd(ShowResult.CLOSED_EARLY), earned()))
        val server = ServerScript(listOf(needs(3, 0)))

        val result = flow(ads, server).run(activity, "f1")

        assertEquals(AdRestoreResult.AdClosedEarly, result)
        assertEquals(3, ads.loadCount)
        assertEquals("no waiting for the server", 1, server.calls)
    }

    @Test
    fun noAvailableAdMeansNothingHappens() = runBlocking {
        assertEquals(AdRestoreResult.AdUnavailable, flow(FakeAds(null as LoadedRewardedAd?), ServerScript(listOf(needsAd))).run(activity, "f1"))
    }

    @Test
    fun anAdThatCannotBeLoadedPartWayStopsThere() = runBlocking {
        val ads = FakeAds(listOf(earned(), null))

        val result = flow(ads, ServerScript(listOf(needs(3, 0)))).run(activity, "f1")

        assertEquals(AdRestoreResult.AdUnavailable, result)
        assertEquals(2, ads.loadCount)
    }

    @Test
    fun anAdThatCouldNotBeShownIsTreatedAsUnavailable() = runBlocking {
        val result = flow(FakeAds(FakeLoadedAd(ShowResult.FAILED)), ServerScript(listOf(needsAd))).run(activity, "f1")

        assertEquals(AdRestoreResult.AdUnavailable, result)
    }

    @Test
    fun givesUpGentlyWhenGoogleNeverConfirms() = runBlocking {
        val server = ServerScript(listOf(needsAd))

        val result = flow(FakeAds(earned()), server, maxAttempts = 4).run(activity, "f1")

        assertEquals(AdRestoreResult.NotConfirmed, result)
        assertEquals("one check before the ad, then four after it", 5, server.calls)
    }

    @Test
    fun keepsWaitingWhileConfirmationsForAllThreeAdsTrickleIn() = runBlocking {
        // After the third ad the server has heard about one, then two, then all three.
        val server = ServerScript(listOf(needs(3, 0), needs(3, 1), needs(3, 2), Result.success(friend(6))))

        val result = flow(FakeAds(List(3) { earned() }), server).run(activity, "f1")

        assertEquals(AdRestoreResult.Restored(friend(6)), result)
    }

    @Test
    fun aRealServerErrorWhileWaitingStopsTheWaiting() = runBlocking {
        val broken = Result.failure<FriendSummaryDto>(Exception("Couldn't connect"))
        val server = ServerScript(listOf(needsAd, broken))

        val result = flow(FakeAds(earned()), server).run(activity, "f1")

        assertEquals(AdRestoreResult.Failed("Couldn't connect"), result)
        assertTrue(server.calls == 2)
    }

    @Test
    fun aMissingUserIdStopsBeforeTheAdIsShown() = runBlocking {
        val ad = earned()
        val noProfile = RestoreStreakWithAd(
            ads = FakeAds(ad),
            adUnitId = "unit",
            myUserId = { Result.failure(Exception("offline")) },
            restore = ServerScript(listOf(needsAd))::restore,
            retryDelayMillis = 0,
        )

        assertEquals(AdRestoreResult.Failed("offline"), noProfile.run(activity, "f1"))
        assertEquals("not shown", ad.userId)
    }

    @Test
    fun theServersCountsAreReadFromItsAnswer() {
        val refusal = adRewardRequired(ErrorResponse(status = 402, error = "Payment Required", adsRequired = 3, adsWatched = 1))

        assertEquals(3, refusal.required)
        assertEquals(1, refusal.watched)
    }

    @Test
    fun anAnswerWithoutCountsMeansOneAdAndNoneSeen() {
        val refusal = adRewardRequired(ErrorResponse(status = 402, error = "Payment Required"))

        assertEquals(1, refusal.required)
        assertEquals(0, refusal.watched)
        assertEquals(1, adRewardRequired(null).required)
    }

    @Test
    fun oddCountsAreKeptSensible() {
        val refusal = adRewardRequired(ErrorResponse(status = 402, error = "Payment Required", adsRequired = 0, adsWatched = 7))

        assertEquals(1, refusal.required)
        assertEquals("never more watched than needed", 1, refusal.watched)
    }
}
