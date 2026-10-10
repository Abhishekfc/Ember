package com.emigo.app.ads

import android.app.Activity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private class MemoryStorage : GalleryUnlockStorage {
    override var hasPass = false
    override var day = ""
    override var unlocksOnThatDay = 0
}

private class AdThatEnds(private val result: ShowResult) : LoadedRewardedAd {
    var userId: String? = "not shown"

    override suspend fun show(activity: Activity, userId: String?, customData: String?): ShowResult {
        this.userId = userId
        return result
    }
}

private class AdSupply(var ad: LoadedRewardedAd?) : RewardedAds {
    override suspend fun load(activity: Activity, adUnitId: String): LoadedRewardedAd? = ad
}

class GalleryUnlockTest {

    private var today = LocalDate.of(2026, 10, 8)
    private val storage = MemoryStorage()
    private val unlock = GalleryUnlock(storage) { today }

    @Test
    fun twoAdsEarnOnePass() {
        assertFalse(unlock.onAdWatched())
        assertEquals(1, unlock.adsWatched)
        assertFalse(unlock.hasPass)

        assertTrue(unlock.onAdWatched())

        assertTrue(unlock.hasPass)
        assertEquals("progress starts over for the next photo", 0, unlock.adsWatched)
    }

    @Test
    fun aPassIsKeptUntilAPhotoIsSent() {
        unlock.onAdWatched()
        unlock.onAdWatched()

        // Opening the picker and backing out changes nothing: only usePass() spends it.
        assertTrue(unlock.hasPass)
        unlock.usePass()
        assertFalse(unlock.hasPass)
    }

    @Test
    fun aPassSurvivesRestartingTheApp() {
        unlock.onAdWatched()
        unlock.onAdWatched()

        val afterRestart = GalleryUnlock(storage) { today }

        assertTrue(afterRestart.hasPass)
    }

    @Test
    fun theDailyLimitIsEnforced() {
        repeat(MAX_GALLERY_UNLOCKS_PER_DAY) {
            unlock.onAdWatched()
            unlock.onAdWatched()
            unlock.usePass()
        }

        assertEquals(0, unlock.unlocksLeftToday)
        assertFalse("a sixth ad earns nothing", unlock.onAdWatched())
        assertEquals(0, unlock.adsWatched)
    }

    @Test
    fun theLimitStartsOverTomorrow() {
        repeat(MAX_GALLERY_UNLOCKS_PER_DAY) {
            unlock.onAdWatched()
            unlock.onAdWatched()
            unlock.usePass()
        }

        today = today.plusDays(1)

        assertEquals(MAX_GALLERY_UNLOCKS_PER_DAY, unlock.unlocksLeftToday)
        unlock.onAdWatched()
        assertTrue(unlock.onAdWatched())
    }

    @Test
    fun theUnlocksLeftCountDownThroughTheDay() {
        assertEquals(MAX_GALLERY_UNLOCKS_PER_DAY, unlock.unlocksLeftToday)
        unlock.onAdWatched()
        unlock.onAdWatched()
        assertEquals(MAX_GALLERY_UNLOCKS_PER_DAY - 1, unlock.unlocksLeftToday)
    }

    // The flow that shows the ads.

    private val activity = object : Activity() {}

    private fun watcher(ad: LoadedRewardedAd?) = WatchAdForGallery(AdSupply(ad), "unit", unlock)

    @Test
    fun theFirstAdReportsProgressAndTheSecondUnlocks() = runBlocking {
        val watcher = watcher(AdThatEnds(ShowResult.EARNED))

        assertEquals(GalleryAdResult.Progress(1), watcher.watchOne(activity))
        assertEquals(GalleryAdResult.Unlocked, watcher.watchOne(activity))
        assertTrue(unlock.hasPass)
    }

    @Test
    fun closingAnAdEarlyCountsForNothing() = runBlocking {
        val result = watcher(AdThatEnds(ShowResult.CLOSED_EARLY)).watchOne(activity)

        assertEquals(GalleryAdResult.AdClosedEarly, result)
        assertEquals(0, unlock.adsWatched)
    }

    @Test
    fun noAvailableAdCountsForNothing() = runBlocking {
        assertEquals(GalleryAdResult.AdUnavailable, watcher(null).watchOne(activity))
        assertEquals(GalleryAdResult.AdUnavailable, watcher(AdThatEnds(ShowResult.FAILED)).watchOne(activity))
        assertEquals(0, unlock.adsWatched)
    }

    @Test
    fun noAdIsShownOnceTodaysLimitIsUsedUp() = runBlocking {
        repeat(MAX_GALLERY_UNLOCKS_PER_DAY) {
            unlock.onAdWatched()
            unlock.onAdWatched()
            unlock.usePass()
        }
        val ad = AdThatEnds(ShowResult.EARNED)

        assertEquals(GalleryAdResult.DailyLimitReached, watcher(ad).watchOne(activity))
        assertEquals("not shown", ad.userId)
    }

    @Test
    fun theServerIsNotToldAboutGalleryAds() = runBlocking {
        val ad = AdThatEnds(ShowResult.EARNED)

        watcher(ad).watchOne(activity)

        assertEquals(null, ad.userId)
    }
}
