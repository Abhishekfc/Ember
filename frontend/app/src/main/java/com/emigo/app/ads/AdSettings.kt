package com.emigo.app.ads

/** Ads per gallery photo and gallery photos per day when nothing has been set remotely. */
const val DEFAULT_GALLERY_ADS_PER_PHOTO = 3
const val DEFAULT_GALLERY_UNLOCKS_PER_DAY = 5

private const val MIN_GALLERY_ADS_PER_PHOTO = 1
private const val MAX_GALLERY_ADS_PER_PHOTO = 10
private const val MIN_GALLERY_UNLOCKS_PER_DAY = 1
private const val MAX_GALLERY_UNLOCKS_PER_DAY_LIMIT = 50

/**
 * The ad rules that can be changed without releasing a new app version: set in the Firebase
 * console (Remote Config) and picked up by phones within about an hour. See [RemoteAdSettings].
 *
 * - [adsEnabled]: the safety switch. False turns every ad off at once: nobody is shown an ad, and
 *   the streak and gallery screens offer Emigo Gold only, as before ads existed.
 * - [galleryAdsPerPhoto]: ads to watch to send one gallery photo.
 * - [galleryUnlocksPerDay]: gallery photos one account can unlock with ads in a day.
 *
 * How many ads a streak restore needs is not here: the server decides that (Railway's
 * `ADMOB_RESTORE_ADS_REQUIRED`), because it has to agree with the app.
 */
data class AdSettings(
    val adsEnabled: Boolean = true,
    val galleryAdsPerPhoto: Int = DEFAULT_GALLERY_ADS_PER_PHOTO,
    val galleryUnlocksPerDay: Int = DEFAULT_GALLERY_UNLOCKS_PER_DAY,
) {
    companion object {
        /** Builds the settings from whatever the console held. Anything missing or unusable falls back
         * to the default, and numbers are held to a sane range, so a typo in the console (a 0, a
         * 500) can never leave a screen with no ads to watch, or an endless number of them. */
        fun fromRemote(adsEnabled: Boolean?, galleryAdsPerPhoto: Long?, galleryUnlocksPerDay: Long?) = AdSettings(
            adsEnabled = adsEnabled ?: true,
            galleryAdsPerPhoto = (galleryAdsPerPhoto ?: DEFAULT_GALLERY_ADS_PER_PHOTO.toLong())
                .coerceIn(MIN_GALLERY_ADS_PER_PHOTO.toLong(), MAX_GALLERY_ADS_PER_PHOTO.toLong()).toInt(),
            galleryUnlocksPerDay = (galleryUnlocksPerDay ?: DEFAULT_GALLERY_UNLOCKS_PER_DAY.toLong())
                .coerceIn(MIN_GALLERY_UNLOCKS_PER_DAY.toLong(), MAX_GALLERY_UNLOCKS_PER_DAY_LIMIT.toLong()).toInt(),
        )
    }
}

/** Where the current [AdSettings] come from. An interface so everything that depends on the
 * numbers can be tested without Firebase. */
fun interface AdSettingsProvider {
    fun current(): AdSettings
}
