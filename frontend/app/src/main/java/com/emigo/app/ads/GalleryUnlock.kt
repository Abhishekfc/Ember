package com.emigo.app.ads

import android.app.Activity
import android.content.Context
import java.time.LocalDate

/** What survives closing the app: whether an unlock is waiting to be used, how many ads are
 * already watched toward the next one, and how many unlocks were earned today. */
interface GalleryUnlockStorage {
    var hasPass: Boolean

    /** Ads watched toward the next unlock. Saved so that an ad that fails to load part way (or the
     * app being closed) never costs the ones already watched. */
    var adsWatched: Int

    /** The day [unlocksOnThatDay] was counted for, as `2026-10-08`; empty before the first one. */
    var day: String
    var unlocksOnThatDay: Int
}

class SharedPrefsGalleryUnlockStorage(context: Context) : GalleryUnlockStorage {
    // Plain SharedPreferences, read synchronously, so the camera's very first frame already knows
    // whether the gallery button is open (same reasoning as SubscriptionRepository.isGoldMemberSync).
    private val prefs = context.getSharedPreferences("ember_gallery_unlock", Context.MODE_PRIVATE)

    override var hasPass: Boolean
        get() = prefs.getBoolean("has_pass", false)
        set(value) = prefs.edit().putBoolean("has_pass", value).apply()

    override var adsWatched: Int
        get() = prefs.getInt("ads_watched", 0)
        set(value) = prefs.edit().putInt("ads_watched", value).apply()

    override var day: String
        get() = prefs.getString("day", "").orEmpty()
        set(value) = prefs.edit().putString("day", value).apply()

    override var unlocksOnThatDay: Int
        get() = prefs.getInt("unlocks", 0)
        set(value) = prefs.edit().putInt("unlocks", value).apply()
}

/**
 * Sending a photo from the gallery is an Emigo Gold perk. Without Gold, watching
 * [adsPerPhoto] ads earns one photo: a "pass" that is kept until a gallery photo is
 * actually sent, so picking a photo and then changing your mind costs nothing.
 *
 * This is checked in the app only. The server can't tell a gallery photo from a camera photo, so
 * unlike restoring a streak it has nothing to enforce; Gold's own gallery lock works the same way.
 */
class GalleryUnlock(
    private val storage: GalleryUnlockStorage,
    /** The current rules (see [AdSettings]); read on every use, so a change made in the Firebase
     * console applies as soon as the phone has fetched it. */
    private val settings: AdSettingsProvider = AdSettingsProvider { AdSettings() },
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    /** Are ads on at all? False is the safety switch: nothing shows an ad, and the screens offer
     * Emigo Gold only. */
    val adsEnabled: Boolean get() = settings.current().adsEnabled

    /** Ads to watch for one gallery photo. Progress toward it is saved (see
     * [GalleryUnlockStorage.adsWatched]), so a later ad that cannot be loaded never loses the ones
     * already watched. */
    val adsPerPhoto: Int get() = settings.current().galleryAdsPerPhoto

    /** Gallery photos a free account can unlock in one day. Without a ceiling, someone could watch
     * ads all day instead of getting Emigo Gold. */
    val unlocksPerDay: Int get() = settings.current().galleryUnlocksPerDay

    /** Ads watched toward the next pass. Saved, so it survives closing the app. */
    val adsWatched: Int get() = storage.adsWatched

    val hasPass: Boolean get() = storage.hasPass

    val unlocksLeftToday: Int
        get() {
            val usedToday = if (storage.day == today().toString()) storage.unlocksOnThatDay else 0
            return (unlocksPerDay - usedToday).coerceAtLeast(0)
        }

    /** Counts one finished ad. True when it was the last one needed and a pass was granted. */
    fun onAdWatched(): Boolean {
        if (unlocksLeftToday == 0) return false
        storage.adsWatched++
        if (storage.adsWatched < adsPerPhoto) return false
        storage.adsWatched = 0
        storage.hasPass = true
        val day = today().toString()
        storage.unlocksOnThatDay = (if (storage.day == day) storage.unlocksOnThatDay else 0) + 1
        storage.day = day
        return true
    }

    /** Called when a gallery photo has been sent. */
    fun usePass() {
        storage.hasPass = false
    }
}

/** How one gallery ad ended. */
sealed interface GalleryAdResult {
    /** Watched. [watched] of the ads one photo needs are done; the pass is not earned yet. */
    data class Progress(val watched: Int) : GalleryAdResult

    /** That was the last ad: the gallery is open for one photo. */
    data object Unlocked : GalleryAdResult

    data object AdUnavailable : GalleryAdResult
    data object AdClosedEarly : GalleryAdResult

    /** Today's limit is used up. */
    data object DailyLimitReached : GalleryAdResult
}

/** Shows one ad toward a gallery photo. */
class WatchAdForGallery(
    private val ads: RewardedAds,
    private val adUnitId: String,
    private val unlock: GalleryUnlock,
) {
    suspend fun watchOne(activity: Activity): GalleryAdResult {
        // The remote safety switch: with ads off no ad is shown from here either.
        if (!unlock.adsEnabled) return GalleryAdResult.AdUnavailable
        if (unlock.unlocksLeftToday == 0) return GalleryAdResult.DailyLimitReached
        val ad = ads.load(activity, adUnitId) ?: return GalleryAdResult.AdUnavailable
        // No user id or custom data: nothing on the server depends on this ad.
        return when (ad.show(activity, userId = null, customData = null)) {
            ShowResult.EARNED ->
                if (unlock.onAdWatched()) GalleryAdResult.Unlocked else GalleryAdResult.Progress(unlock.adsWatched)
            ShowResult.CLOSED_EARLY -> GalleryAdResult.AdClosedEarly
            ShowResult.FAILED -> GalleryAdResult.AdUnavailable
        }
    }
}
