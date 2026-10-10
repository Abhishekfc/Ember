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
    override var adsWatched = 0
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
    var loads = 0
    override suspend fun load(activity: Activity, adUnitId: String): LoadedRewardedAd? {
        loads++
        return ad
    }
}

class GalleryUnlockTest {

    private var today = LocalDate.of(2026, 10, 8)
    private val storage = MemoryStorage()

    /** The ad rules, changeable mid-test the way a Firebase console change is for the app. */
    private var settings = AdSettings()
    private val unlock = GalleryUnlock(storage, AdSettingsProvider { settings }) { today }

    /** Watches every ad one photo costs. Written against the current rules, so these tests stay right
     * whatever the numbers are. */
    private fun GalleryUnlock.watchAllAds() = repeat(adsPerPhoto) { onAdWatched() }

    @Test
    fun theDefaultsAreThreeAdsPerPhotoAndFivePhotosADay() {
        assertEquals(3, unlock.adsPerPhoto)
        assertEquals(5, unlock.unlocksPerDay)
        assertTrue(unlock.adsEnabled)
    }

    @Test
    fun threeAdsEarnOnePass() {
        // Every ad before the last only counts toward the pass.
        repeat(unlock.adsPerPhoto - 1) { index ->
            assertFalse(unlock.onAdWatched())
            assertEquals(index + 1, unlock.adsWatched)
            assertFalse(unlock.hasPass)
        }

        assertTrue(unlock.onAdWatched())

        assertTrue(unlock.hasPass)
        assertEquals("progress starts over for the next photo", 0, unlock.adsWatched)
    }

    @Test
    fun progressTowardAPassSurvivesRestartingTheApp() {
        // A third ad that would not load must not lose the first two.
        unlock.onAdWatched()
        unlock.onAdWatched()
        assertFalse(unlock.hasPass)

        val afterRestart = GalleryUnlock(storage, AdSettingsProvider { settings }) { today }

        assertEquals(2, afterRestart.adsWatched)
    }

    @Test
    fun aPassIsKeptUntilAPhotoIsSent() {
        unlock.watchAllAds()

        // Opening the picker and backing out changes nothing: only usePass() spends it.
        assertTrue(unlock.hasPass)
        unlock.usePass()
        assertFalse(unlock.hasPass)
    }

    @Test
    fun aPassSurvivesRestartingTheApp() {
        unlock.watchAllAds()

        val afterRestart = GalleryUnlock(storage, AdSettingsProvider { settings }) { today }

        assertTrue(afterRestart.hasPass)
    }

    @Test
    fun theDailyLimitIsEnforced() {
        repeat(unlock.unlocksPerDay) {
            unlock.watchAllAds()
            unlock.usePass()
        }

        assertEquals(0, unlock.unlocksLeftToday)
        assertFalse("another ad earns nothing", unlock.onAdWatched())
        assertEquals(0, unlock.adsWatched)
    }

    @Test
    fun theLimitStartsOverTomorrow() {
        repeat(unlock.unlocksPerDay) {
            unlock.watchAllAds()
            unlock.usePass()
        }

        today = today.plusDays(1)

        assertEquals(unlock.unlocksPerDay, unlock.unlocksLeftToday)
        unlock.watchAllAds()
        assertTrue(unlock.hasPass)
    }

    @Test
    fun theUnlocksLeftCountDownThroughTheDay() {
        assertEquals(unlock.unlocksPerDay, unlock.unlocksLeftToday)
        unlock.watchAllAds()
        assertEquals(unlock.unlocksPerDay - 1, unlock.unlocksLeftToday)
    }

    // Changing the rules from the Firebase console, with no app update.

    @Test
    fun moreAdsPerPhotoTakesEffectAtOnce() {
        settings = AdSettings(galleryAdsPerPhoto = 5)

        repeat(4) { assertFalse(unlock.onAdWatched()) }
        assertTrue("the fifth earns it", unlock.onAdWatched())
    }

    @Test
    fun fewerAdsPerPhotoTakesEffectAtOnce() {
        settings = AdSettings(galleryAdsPerPhoto = 1)

        assertTrue(unlock.onAdWatched())
        assertTrue(unlock.hasPass)
    }

    @Test
    fun loweringTheNumberOfAdsAfterSomeWereWatchedNeedsOnlyOneMore() {
        // Two watched under the old rule of 3; the rule drops to 2.
        unlock.onAdWatched()
        unlock.onAdWatched()
        settings = AdSettings(galleryAdsPerPhoto = 2)

        assertTrue("the next ad completes it", unlock.onAdWatched())
        assertEquals(0, unlock.adsWatched)
    }

    @Test
    fun aLowerDailyLimitAppliesToWhatWasAlreadyUsedToday() {
        unlock.watchAllAds()
        unlock.usePass()
        unlock.watchAllAds()
        unlock.usePass()
        settings = AdSettings(galleryUnlocksPerDay = 2)

        assertEquals(0, unlock.unlocksLeftToday)
        assertFalse(unlock.onAdWatched())
    }

    @Test
    fun aHigherDailyLimitGivesMoreUnlocksToday() {
        repeat(unlock.unlocksPerDay) {
            unlock.watchAllAds()
            unlock.usePass()
        }
        assertEquals(0, unlock.unlocksLeftToday)

        settings = AdSettings(galleryUnlocksPerDay = 8)

        assertEquals(3, unlock.unlocksLeftToday)
    }

    // The flow that shows the ads.

    private val activity = object : Activity() {}

    private fun watcher(ad: LoadedRewardedAd?) = WatchAdForGallery(AdSupply(ad), "unit", unlock)

    @Test
    fun eachAdReportsProgressAndTheLastOneUnlocks() = runBlocking {
        val watcher = watcher(AdThatEnds(ShowResult.EARNED))

        for (watched in 1 until unlock.adsPerPhoto) {
            assertEquals(GalleryAdResult.Progress(watched), watcher.watchOne(activity))
        }
        assertEquals(GalleryAdResult.Unlocked, watcher.watchOne(activity))
        assertTrue(unlock.hasPass)
    }

    @Test
    fun aFailedLaterAdKeepsTheProgressSoTheViewerContinuesFromWhereTheyWere() = runBlocking {
        val supply = AdSupply(AdThatEnds(ShowResult.EARNED))
        val watcher = WatchAdForGallery(supply, "unit", unlock)
        watcher.watchOne(activity)
        watcher.watchOne(activity)

        // The third ad cannot be had (no fill, bad moment on the network).
        supply.ad = null
        assertEquals(GalleryAdResult.AdUnavailable, watcher.watchOne(activity))
        assertEquals("the two watched ads still count", 2, unlock.adsWatched)

        // Trying again later: one more ad is all that is left.
        supply.ad = AdThatEnds(ShowResult.EARNED)
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
        repeat(unlock.unlocksPerDay) {
            unlock.watchAllAds()
            unlock.usePass()
        }
        val ad = AdThatEnds(ShowResult.EARNED)

        assertEquals(GalleryAdResult.DailyLimitReached, watcher(ad).watchOne(activity))
        assertEquals("not shown", ad.userId)
    }

    @Test
    fun theSafetySwitchStopsEveryGalleryAd() = runBlocking {
        settings = AdSettings(adsEnabled = false)
        val supply = AdSupply(AdThatEnds(ShowResult.EARNED))

        val result = WatchAdForGallery(supply, "unit", unlock).watchOne(activity)

        assertEquals(GalleryAdResult.AdUnavailable, result)
        assertEquals("no ad was even requested", 0, supply.loads)
        assertEquals(0, unlock.adsWatched)
    }

    @Test
    fun turningTheSafetySwitchBackOnBringsAdsBack() = runBlocking {
        settings = AdSettings(adsEnabled = false)
        val watcher = watcher(AdThatEnds(ShowResult.EARNED))
        assertEquals(GalleryAdResult.AdUnavailable, watcher.watchOne(activity))

        settings = AdSettings(adsEnabled = true)

        assertEquals(GalleryAdResult.Progress(1), watcher.watchOne(activity))
    }

    @Test
    fun theServerIsNotToldAboutGalleryAds() = runBlocking {
        val ad = AdThatEnds(ShowResult.EARNED)

        watcher(ad).watchOne(activity)

        assertEquals(null, ad.userId)
    }
}
