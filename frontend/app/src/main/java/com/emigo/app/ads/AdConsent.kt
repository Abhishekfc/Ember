package com.emigo.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Google's consent step (UMP). Where the law requires it, such as the EU and UK, the viewer gets
 * Google's own consent form before the first ad request; elsewhere, including India, this returns
 * true at once and shows nothing. Nobody who never asks for an ad ever reaches it. */
class AdConsent(context: Context) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)

    /** Whether Google has been told about this viewer yet this app run. */
    private var isInfoUpdated = false

    /** Asked once per app run; later calls just read the answer. */
    private var hasRefreshed = false

    /** True when ads may be requested now. May show the consent form on top of [activity]. */
    suspend fun canRequestAds(activity: Activity): Boolean {
        if (hasRefreshed) return consentInformation.canRequestAds()
        updateInfo(activity)
        if (isInfoUpdated) {
            suspendCancellableCoroutine { continuation ->
                // Shows the form only if one is required and not already answered.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
        hasRefreshed = true
        return consentInformation.canRequestAds()
    }

    /** True when this viewer must be able to change their ad choice later (the EU, UK and
     * Switzerland). Only asks Google where the viewer is; it never shows the form by itself. */
    suspend fun isPrivacyOptionsRequired(activity: Activity): Boolean {
        updateInfo(activity)
        return consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /** Opens Google's form where the viewer changes or takes back their choice. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    private suspend fun updateInfo(activity: Activity) {
        if (isInfoUpdated) return
        isInfoUpdated = suspendCancellableCoroutine { continuation ->
            consentInformation.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                { if (continuation.isActive) continuation.resume(true) },
                { if (continuation.isActive) continuation.resume(false) },
            )
        }
    }
}
