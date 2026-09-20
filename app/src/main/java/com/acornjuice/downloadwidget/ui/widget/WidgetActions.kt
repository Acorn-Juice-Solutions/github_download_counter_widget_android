package com.acornjuice.downloadwidget.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.acornjuice.downloadwidget.ui.settings.MainActivity

/**
 * Builders for the [PendingIntent]s a widget attaches to its clickable regions.
 *
 * Extracted so [WidgetRenderer] and provider tests can construct real `PendingIntent`s
 * without pulling in the full provider logic.
 */
object WidgetActions {

    /** Broadcast action fired by the widget's refresh button. */
    const val ACTION_REFRESH = "com.acornjuice.downloadwidget.ACTION_REFRESH"

    /**
     * Internal broadcast fired by [com.acornjuice.downloadwidget.ui.widget.BaseDownloadWidgetProvider]
     * after a tap-refresh has finished the network call: signals the provider to apply the
     * terminal render from a fresh `onReceive`, so the follow-up `updateAppWidget` lands
     * comfortably past the launcher's dedup window (see class kdoc).
     */
    const val ACTION_APPLY_FOLLOWUP = "com.acornjuice.downloadwidget.ACTION_APPLY_FOLLOWUP"

    private const val URI_SCHEME = "widget"
    private const val URI_HOST_SETTINGS = "settings"
    private const val URI_HOST_REFRESH = "refresh"

    /**
     * Opens [MainActivity] scoped to [widgetId] so the user can edit that widget's config.
     */
    fun settingsPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.Builder().scheme(URI_SCHEME).authority(URI_HOST_SETTINGS)
                .appendPath(widgetId.toString()).build()
        }
        return PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Sends [ACTION_REFRESH] to [providerClass] for [widgetId].
     */
    fun refreshPendingIntent(context: Context, widgetId: Int, providerClass: Class<*>): PendingIntent {
        val intent = Intent(context, providerClass).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.Builder().scheme(URI_SCHEME).authority(URI_HOST_REFRESH)
                .appendPath(widgetId.toString()).build()
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
