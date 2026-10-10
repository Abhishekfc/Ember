package com.emigo.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** The real thing: AdMob rewarded ads. The ads SDK is started here, the first time an ad is asked
 * for, not at app launch, so people who never watch one (everyone with Emigo Gold) never start it. */
class AdMobRewardedAds(
    private val context: Context,
    private val consent: AdConsent,
    private val settings: AdSettingsProvider = AdSettingsProvider { AdSettings() },
) : RewardedAds {

    private val isStarted = AtomicBoolean(false)

    override suspend fun load(activity: Activity, adUnitId: String): LoadedRewardedAd? {
        // The safety switch (Firebase Remote Config `ads_enabled`): when it is off no ad is ever
        // requested, whichever screen asked, and the consent form isn't shown either.
        if (!settings.current().adsEnabled) return null
        if (!consent.canRequestAds(activity)) return null
        if (isStarted.compareAndSet(false, true)) {
            // Google asks for this off the main thread.
            withContext(Dispatchers.IO) { MobileAds.initialize(context) }
        }
        // RewardedAd.load must be called on the main thread.
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                RewardedAd.load(
                    context,
                    adUnitId,
                    AdRequest.Builder().build(),
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            if (continuation.isActive) continuation.resume(AdMobLoadedAd(ad))
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }
        }
    }
}

private class AdMobLoadedAd(private val ad: RewardedAd) : LoadedRewardedAd {

    override suspend fun show(activity: Activity, userId: String?, customData: String?): ShowResult =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                var earned = false
                if (userId != null) {
                    ad.setServerSideVerificationOptions(
                        ServerSideVerificationOptions.Builder()
                            .setUserId(userId)
                            .apply { customData?.let { setCustomData(it) } }
                            .build(),
                    )
                }
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        if (continuation.isActive) continuation.resume(if (earned) ShowResult.EARNED else ShowResult.CLOSED_EARLY)
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        if (continuation.isActive) continuation.resume(ShowResult.FAILED)
                    }
                }
                ad.show(activity) { earned = true }
            }
        }
}
