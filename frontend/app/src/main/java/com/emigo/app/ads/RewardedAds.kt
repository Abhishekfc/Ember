package com.emigo.app.ads

import android.app.Activity

/** How a shown rewarded ad ended. */
enum class ShowResult {
    /** Watched to the end; the reward is earned. */
    EARNED,

    /** Closed before the end; no reward. */
    CLOSED_EARLY,

    /** Loaded, but couldn't be put on screen. */
    FAILED,
}

/** Loads rewarded ads. The flows built on top of this ([RestoreStreakWithAd], [WatchAdForGallery])
 * only know this small interface, so they can be tested without the ads SDK. */
interface RewardedAds {
    /** One ad, loaded and ready to show, or null when none can be had right now (no connection,
     * nothing to show, or the viewer's consent choice leaves us unable to request ads). */
    suspend fun load(activity: Activity, adUnitId: String): LoadedRewardedAd?
}

interface LoadedRewardedAd {
    /** Shows the ad full-screen and returns once it is closed. [userId] and [customData], when
     * given, are handed to Google and come back, signed, on the callback to our server (see the
     * backend's AdRewardController); that is how the server learns the ad was really watched. */
    suspend fun show(activity: Activity, userId: String?, customData: String?): ShowResult
}
