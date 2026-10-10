package com.emigo.app.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The values that come from the Firebase console are typed by a person, so anything missing or
 * silly must fall back to something safe rather than break a screen. */
class AdSettingsTest {

    @Test
    fun nothingPublishedMeansTheBuiltInDefaults() {
        val settings = AdSettings.fromRemote(adsEnabled = null, galleryAdsPerPhoto = null, galleryUnlocksPerDay = null)

        assertEquals(AdSettings(), settings)
        assertTrue(settings.adsEnabled)
        assertEquals(3, settings.galleryAdsPerPhoto)
        assertEquals(5, settings.galleryUnlocksPerDay)
    }

    @Test
    fun publishedValuesAreUsed() {
        val settings = AdSettings.fromRemote(adsEnabled = true, galleryAdsPerPhoto = 4, galleryUnlocksPerDay = 7)

        assertEquals(AdSettings(adsEnabled = true, galleryAdsPerPhoto = 4, galleryUnlocksPerDay = 7), settings)
    }

    @Test
    fun theSafetySwitchCanBeTurnedOff() {
        assertFalse(AdSettings.fromRemote(adsEnabled = false, galleryAdsPerPhoto = null, galleryUnlocksPerDay = null).adsEnabled)
    }

    @Test
    fun aZeroOrNegativeNumberOfAdsBecomesOne() {
        // A photo with no ads to watch would make the gallery free by a typo.
        assertEquals(1, AdSettings.fromRemote(null, galleryAdsPerPhoto = 0, galleryUnlocksPerDay = null).galleryAdsPerPhoto)
        assertEquals(1, AdSettings.fromRemote(null, galleryAdsPerPhoto = -3, galleryUnlocksPerDay = null).galleryAdsPerPhoto)
    }

    @Test
    fun anAbsurdNumberOfAdsIsHeldToTen() {
        assertEquals(10, AdSettings.fromRemote(null, galleryAdsPerPhoto = 500, galleryUnlocksPerDay = null).galleryAdsPerPhoto)
    }

    @Test
    fun aZeroOrNegativeDailyLimitBecomesOne() {
        assertEquals(1, AdSettings.fromRemote(null, galleryAdsPerPhoto = null, galleryUnlocksPerDay = 0).galleryUnlocksPerDay)
        assertEquals(1, AdSettings.fromRemote(null, galleryAdsPerPhoto = null, galleryUnlocksPerDay = -1).galleryUnlocksPerDay)
    }

    @Test
    fun anAbsurdDailyLimitIsHeldToFifty() {
        assertEquals(50, AdSettings.fromRemote(null, galleryAdsPerPhoto = null, galleryUnlocksPerDay = 100_000).galleryUnlocksPerDay)
    }

    @Test
    fun theLimitsThemselvesAreAllowed() {
        assertEquals(1, AdSettings.fromRemote(null, 1, 1).galleryAdsPerPhoto)
        assertEquals(10, AdSettings.fromRemote(null, 10, 50).galleryAdsPerPhoto)
        assertEquals(50, AdSettings.fromRemote(null, 10, 50).galleryUnlocksPerDay)
    }

    @Test
    fun eachSettingFallsBackOnItsOwn() {
        // Only the daily limit was published: the other two keep their defaults.
        val settings = AdSettings.fromRemote(adsEnabled = null, galleryAdsPerPhoto = null, galleryUnlocksPerDay = 9)

        assertTrue(settings.adsEnabled)
        assertEquals(3, settings.galleryAdsPerPhoto)
        assertEquals(9, settings.galleryUnlocksPerDay)
    }
}
