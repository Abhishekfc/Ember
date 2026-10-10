package com.emigo.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Stops the widget when the account behind it is gone. The one place that does it, used by every
 * way an account can end: signing out in the app, the app's own background refresh finding the
 * session dead, a push arriving for an account that is no longer signed in, and Firebase ending a
 * session by itself (for example after the password was changed on another phone).
 *
 * Clears the cached friend photo and name (and deletes the photo file), the Gold-only featured
 * friends choice, redraws the widget empty, and cancels its background refresh. The widget reads
 * its own saved copy whether anyone is signed in or not, so without this a friend's private photo
 * would stay on the home screen, and keep being replaced by new ones, after the account was gone. */
object WidgetSession {

    suspend fun clear(context: Context) {
        WidgetPhotoStore(context).clear()
        WidgetPreferenceStore(context).clear()
        EmberWidget().updateAll(context)
        WidgetUpdateWorker.cancel(context)
    }
}
