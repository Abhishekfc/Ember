package com.emigo.app.data.local

import android.content.Context

/** Whether Camera's own "swipe to explore" onboarding hint has ever been dismissed — plain
 * SharedPreferences, not DataStore, so CameraViewModel (created once at app start, before this
 * screen is ever actually composed — Camera is the app's own opening page) can seed the hint's
 * initial visibility synchronously with no async gap. This hint is specifically for brand-new
 * users; a returning user must never see it pop in for even a frame before disappearing again,
 * the same reasoning SubscriptionRepository's own syncPrefs already established for isGoldMember. */
class CameraHintPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("ember_camera_hint", Context.MODE_PRIVATE)
    private val dismissedKey = "swipe_hint_dismissed"

    fun isDismissed(): Boolean = prefs.getBoolean(dismissedKey, false)

    fun dismiss() {
        prefs.edit().putBoolean(dismissedKey, true).apply()
    }
}
