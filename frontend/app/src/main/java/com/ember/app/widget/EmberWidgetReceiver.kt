package com.ember.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class EmberWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EmberWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Placing a widget starts the background refresh cycle even if the app is never opened.
        WidgetUpdateWorker.schedule(context)
    }
}
