package com.emigo.app

import android.content.Context
import androidx.annotation.StringRes

/** Looks up text from `strings.xml` for code that has no Compose scope or Activity of its own,
 * mainly ViewModels, so their messages live in the same place as every screen's. Holds the
 * application context, so it never keeps an Activity alive. */
class StringProvider(private val context: Context) {
    fun get(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)
}
