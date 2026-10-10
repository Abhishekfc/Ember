package com.emigo.app.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The Activity behind a Compose [Context], which is usually wrapped one or more times. Showing an
 * ad needs the real Activity to put the ad on top of. */
fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
