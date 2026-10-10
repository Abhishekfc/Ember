package com.emigo.app.ads

import android.app.Activity
import android.content.Context
import java.time.LocalDate

/** Ads to watch for one gallery photo. */
const val ADS_PER_GALLERY_PHOTO = 2

/** Gallery photos a free account can unlock in one day. Without a ceiling, someone could watch ads
 * all day instead of getting Emigo Gold.
 *
 * Five a day: ten ads buys five moments, which is plenty to be useful and still a reason to
 * consider Gold. */
const val MAX_GALLERY_UNLOCKS_PER_DAY = 5

/** What survives closing the app: whether an unlock is waiting to be used, and how many were
 * earned today. */
interface GalleryUnlockStorage {
    var hasPass: Boolean

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

    override var day: String
        get() = prefs.getString("day", "").orEmpty()
        set(value) = prefs.edit().putString("day", value).apply()

    override var unlocksOnThatDay: Int
        get() = prefs.getInt("unlocks", 0)
        set(value) = prefs.edit().putInt("unlocks", value).apply()
}

/**
 * Sending a photo from the gallery is an Emigo Gold perk. Without Gold, watching
 * [ADS_PER_GALLERY_PHOTO] ads earns one photo: a "pass" that is kept until a gallery photo is
 * actually sent, so picking a photo and then changing your mind costs nothing.
 *
 * This is checked in the app only. The server can't tell a gallery photo from a camera photo, so
 * unlike restoring a streak it has nothing to enforce; Gold's own gallery lock works the same way.
 */
class GalleryUnlock(
    private val storage: GalleryUnlockStorage,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    /** Ads watched toward the next pass. Kept while the app runs, not across restarts. */
    var adsWatched: Int = 0
        private set

    val hasPass: Boolean get() = storage.hasPass

    val unlocksLeftToday: Int
        get() {
            val usedToday = if (storage.day == today().toString()) storage.unlocksOnThatDay else 0
            return (MAX_GALLERY_UNLOCKS_PER_DAY - usedToday).coerceAtLeast(0)
        }

    /** Counts one finished ad. True when it was the last one needed and a pass was granted. */
    fun onAdWatched(): Boolean {
        if (unlocksLeftToday == 0) return false
        adsWatched++
        if (adsWatched < ADS_PER_GALLERY_PHOTO) return false
        adsWatched = 0
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
    /** Watched. [watched] of [ADS_PER_GALLERY_PHOTO] are done; the pass is not earned yet. */
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
