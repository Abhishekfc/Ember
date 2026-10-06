package com.emigo.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Every external link and contact address the app points users at, in one place — change a URL
 * here and every screen that shows it follows. */
object AppLinks {
    const val TERMS_OF_SERVICE = "https://emigo.live/terms-of-service"
    const val PRIVACY_POLICY = "https://emigo.live/privacy-policy"
    const val SUPPORT_EMAIL = "emigohq@gmail.com"
    const val PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"

    /** Play's "manage this subscription" page; falls back to the general list when no product is known. */
    fun playSubscription(productId: String?, packageName: String): String =
        if (productId != null) "$PLAY_SUBSCRIPTIONS?sku=$productId&package=$packageName" else PLAY_SUBSCRIPTIONS
}

/** Opens [url] in whatever app handles it; silently does nothing if none does. */
fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
