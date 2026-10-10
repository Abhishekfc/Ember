package com.emigo.app.ads

import com.emigo.app.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlinx.coroutines.tasks.await

/** [AdSettingsProvider] backed by Firebase Remote Config. Change the numbers (or flip the safety
 * switch) in the Firebase console, press Publish, and phones pick it up within about an hour; no
 * app update, no server change.
 *
 * The three parameter names to create there:
 * - `ads_enabled` (true/false)
 * - `gallery_ads_per_photo` (number)
 * - `gallery_unlocks_per_day` (number)
 *
 * Nothing is needed in the console to start with: until a value is published (and whenever the
 * phone is offline or Firebase can't be reached) the built-in defaults apply, see [AdSettings]. */
class RemoteAdSettings(
    private val config: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance(),
) : AdSettingsProvider {

    init {
        config.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                // How often a phone may ask Firebase again. An hour in the real app; a minute in the
                // test build, so a console change can be tried without waiting.
                .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 60L else 3_600L)
                .build(),
        )
    }

    /** The last values fetched and activated, read at once (no waiting, no network). Anything not
     * set, or set to something that isn't a number or true/false, becomes the built-in default. */
    override fun current(): AdSettings = AdSettings.fromRemote(
        adsEnabled = published(KEY_ADS_ENABLED) { asBoolean() },
        galleryAdsPerPhoto = published(KEY_GALLERY_ADS_PER_PHOTO) { asLong() },
        galleryUnlocksPerDay = published(KEY_GALLERY_UNLOCKS_PER_DAY) { asLong() },
    )

    /** Asks Firebase for new values, at most once per fetch interval, and switches to them. Safe to
     * call at every app start. A failure (offline, throttled) just keeps the values already held. */
    suspend fun refresh() {
        runCatching { config.fetchAndActivate().await() }
    }

    /** The value for [key] only when one was actually published in the console; null otherwise,
     * including when it can't be read as the type asked for. */
    private fun <T> published(key: String, read: FirebaseRemoteConfigValue.() -> T): T? {
        val value = config.getValue(key)
        if (value.source != FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) return null
        return runCatching { value.read() }.getOrNull()
    }

    private companion object {
        const val KEY_ADS_ENABLED = "ads_enabled"
        const val KEY_GALLERY_ADS_PER_PHOTO = "gallery_ads_per_photo"
        const val KEY_GALLERY_UNLOCKS_PER_DAY = "gallery_unlocks_per_day"
    }
}
