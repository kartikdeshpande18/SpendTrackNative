package com.spendtrack.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object SpendTrackWidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching {
            SpendTrackWidget().updateAll(context)
            SpendTrackQuickActionWidget().updateAll(context)
        }
    }
}
